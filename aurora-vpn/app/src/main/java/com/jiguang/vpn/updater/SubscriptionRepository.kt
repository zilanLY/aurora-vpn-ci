package com.jiguang.vpn.updater

import com.jiguang.vpn.model.ProxyNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 订阅仓库:负责拉取与解析订阅源,并以**流式**方式逐批产出节点,
 * 让 UI 尽早拿到可用线路,不必等待全部来源加载完成。
 *
 * 来源(v1.0.20 起,v2rayfree 因长期无法直连已移除):
 * 1. shaoyou      — 默认来源,base64 + YAML,约 7 条
 * 2. pawdroid     — 约 10 条
 * 3. clashfree    — 每日 Clash YAML,约 300 条
 * 4. mibei        — 米贝分享,约 290 条
 *
 * 全部来源自动排除 ss/ssr 协议。
 * 抓取策略:首签(shao you)单独第一个 emit,其余三源并行抓取,先到先 emit——
 * 避免单一 GitHub 域名超时阻塞整个列表加载。
 */
object SubscriptionRepository {

    val DEFAULT_SOURCE = SourceId.SHAOYOU

    enum class SourceId(val label: String) {
        SHAOYOU("ShaoyouFree"),
        PAWDROID("Pawdroid"),
        CLASHFREE("ClashFree"),
        MIBEI("米贝分享")
    }

    /** 单批产出:[source] 为来源标识,null 表示这是收尾批(全部来源结束)。 */
    data class Batch(val source: SourceId?, val nodes: List<ProxyNode>)

    /** 全部启用来源(默认来源在前)。 */
    fun allSources(): List<SourceId> = SourceId.entries

    /**
     * 流式抓取:
     * - 默认来源(shao you)最先单独 emit,保证默认线路秒级可用;
     * - 其余来源并行发起,每个完成后立即 emit(先到先得);
     * - 全部结束后 emit 收尾批(source == null)。
     */
    fun stream(): Flow<Batch> = flow {
        val def = fetchSingle(DEFAULT_SOURCE)
        if (def.isNotEmpty()) emit(Batch(DEFAULT_SOURCE, def))

        val rest = allSources().filter { it != DEFAULT_SOURCE }
        coroutineScope {
            val deferred = rest.map { src -> async { src to runCatching { fetchSource(src) }.getOrDefault(emptyList()) } }
            for ((src, nodes) in deferred.awaitAll()) {
                if (nodes.isNotEmpty()) emit(Batch(src, dedupNonSs(nodes)))
            }
        }
        emit(Batch(null, emptyList()))
    }.flowOn(Dispatchers.IO)

    /** 单来源抓取(网络在 IO)。 */
    suspend fun fetchSingle(source: SourceId): List<ProxyNode> = withContext(Dispatchers.IO) {
        try {
            dedupNonSs(fetchSource(source))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchSource(source: SourceId): List<ProxyNode> = when (source) {
        SourceId.SHAOYOU -> fetchBase64(Endpoints.SHAOYOU_TXT) + fetchYaml(Endpoints.SHAOYOU_YAML)
        SourceId.PAWDROID -> fetchBase64(Endpoints.PAWDROID)
        SourceId.CLASHFREE -> fetchClashFree()
        SourceId.MIBEI -> fetchMiBei()
    }

    // ---------- 各来源实现 ----------

    private fun fetchBase64(url: String): List<ProxyNode> =
        NodeParser.parseTxt(Fetcher.get(url))

    private fun fetchYaml(url: String): List<ProxyNode> =
        ClashYaml.parse(Fetcher.get(url))

    private fun fetchClashFree(): List<ProxyNode> {
        val zone = ZoneId.of("Asia/Shanghai")
        var last: Exception? = null
        for (back in 0..3) {
            val d = LocalDate.now(zone).minusDays(back.toLong())
            val u = Endpoints.CLASH_DAILY.format(d.year, d.monthValue, d.dayOfMonth)
            try {
                val body = Fetcher.get(u)
                if (body.contains("proxies:")) return ClashYaml.parse(body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("clashfree 无可用日期")
    }

    private fun fetchMiBei(): List<ProxyNode> {
        // 1) HTML 主链
        try {
            val listHtml = Fetcher.get(Endpoints.MIBEI_BASE + "/free-nodes/")
            val detailPath = MIBEI_DETAIL.find(listHtml)?.value
            if (detailPath != null) {
                val detailHtml = Fetcher.get(Endpoints.MIBEI_BASE + detailPath)
                val urls = MIBEI_SUB.findAll(detailHtml).map { it.value }.distinct().toList()
                val ordered = urls.sortedBy { if (it.endsWith(".txt")) 0 else 1 }
                for (u in ordered) {
                    try {
                        val nodes = NodeParser.parseTxt(Fetcher.get(u))
                        if (nodes.isNotEmpty()) return nodes
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        // 2) 日期直猜兜底
        val zone = ZoneId.of("Asia/Shanghai")
        for (back in 0..3) {
            val d = LocalDate.now(zone).minusDays(back.toLong())
            val u = "https://node.mibeifenxiang.com/uploads/%d/%02d/%d%02d%02d.txt"
                .format(d.year, d.monthValue, d.year, d.monthValue, d.dayOfMonth)
            try {
                val nodes = NodeParser.parseTxt(Fetcher.get(u))
                if (nodes.isNotEmpty()) return nodes
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    // ---------- 工具 ----------

    private fun dedupNonSs(nodes: List<ProxyNode>): List<ProxyNode> {
        val seen = HashSet<String>()
        return nodes.filter {
            val low = it.uri.lowercase()
            !low.startsWith("ss://") && !low.startsWith("ssr://") && seen.add(it.uri)
        }
    }

    private val MIBEI_DETAIL = Regex("""/free-nodes/[^"']+\.htm""")
    private val MIBEI_SUB =
        Regex("""https://node\.mibeifenxiang\.com/uploads/\d{4}/\d{2}/\d{8}\.(?:txt|yaml|json)""")

    private object Endpoints {
        const val PAWDROID = "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub"
        const val SHAOYOU_TXT = "https://raw.githubusercontent.com/shaoyouvip/free/main/base64.txt"
        const val SHAOYOU_YAML = "https://raw.githubusercontent.com/shaoyouvip/free/main/all.yaml"
        const val CLASH_DAILY = "https://raw.githubusercontent.com/free-nodes/clashfree/main/clash%04d%02d%02d.yml"
        const val MIBEI_BASE = "https://mibeifenxiang.com"
    }
}

/** 各来源共用的 HTTP 抓取工具。 */
internal object Fetcher {
    const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun get(url: String): String {
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} $url")
            return resp.body?.string() ?: throw IllegalStateException("空响应 $url")
        }
    }
}
