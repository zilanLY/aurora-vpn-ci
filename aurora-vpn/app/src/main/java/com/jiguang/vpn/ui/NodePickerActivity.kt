package com.jiguang.vpn.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jiguang.vpn.core.Link
import com.jiguang.vpn.core.SingBoxConfig
import com.jiguang.vpn.data.LatencyEngine
import com.jiguang.vpn.data.LatencyTester
import com.jiguang.vpn.data.NodeStore
import com.jiguang.vpn.data.RegionResolver
import com.jiguang.vpn.service.JiguangVpnService
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 线路选择子页(v2):
 * - 顶部来源分组芯片:默认来源进页自动测速,其余来源**点击分组时才测速**
 * - 组内搜索 + 地区徽章 + 延迟徽章(边测边刷)
 * - 底部「自动优选」:在已确认可用的节点里选最低延迟
 */
class NodePickerActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var chipBar: LinearLayout
    private lateinit var countView: TextView
    private lateinit var hintView: TextView
    private lateinit var searchInput: EditText
    private lateinit var bestBtn: Button

    private var latencies: MutableMap<String, Int> = HashMap()
    private var currentGroup: String? = null
    private var query = ""
    private var density = 1f

    private val regionCache = HashMap<Int, String>()
    private var lastRenderedCount = 0
    private var autoTestKicked = false

    /** 列表重建防抖:测速回调高频到来时避免全量重建掉帧。 */
    private val renderDebounce = android.os.Handler(android.os.Looper.getMainLooper())
    private val renderDebounceRunnable = Runnable { renderList() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        density = resources.displayMetrics.density
        RegionResolver.init(this)
        LatencyEngine.init(this)
        setContentView(buildLayout())

        latencies = LatencyEngine.current().toMutableMap()
        currentGroup = defaultGroup()
        renderChips()
        renderList()
        updateHint()
        NodeStoreBridge.addListener(storeListener)
        LatencyEngine.addListener(latencyListener)
        resolveRegions()
    }

    override fun onDestroy() {
        NodeStoreBridge.removeListener(storeListener)
        LatencyEngine.removeListener(latencyListener)
        renderDebounce.removeCallbacks(renderDebounceRunnable)
        super.onDestroy()
    }

    private fun defaultGroup(): String? = NodeStore.sources.firstOrNull()

    private val storeListener: () -> Unit = {
        runOnUiThread {
            if (NodeStore.links.size != lastRenderedCount) {
                if (currentGroup == null) currentGroup = defaultGroup()
                renderChips()
                renderList()
                maybeAutoTest()
            }
        }
    }

    private val latencyListener: () -> Unit = {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            latencies = LatencyEngine.current().toMutableMap()
            updateHint()
            renderDebounce.removeCallbacks(renderDebounceRunnable)
            renderDebounce.postDelayed(renderDebounceRunnable, 300)
        }
    }

    /** 默认来源:进页(或数据首次到位)自动测速一次;其余来源点击才测。 */
    private fun maybeAutoTest() {
        if (autoTestKicked) return
        val def = defaultGroup() ?: return
        autoTestKicked = true
        LatencyEngine.ensureIncremental()
    }

    // ---------- 布局 ----------

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        // 顶部:返回 + 标题 + 优选
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = Button(this).apply {
            text = "←"
            textSize = 18f
            setTextColor(Theme.TEXT)
            background = Theme.ripple(Theme.rounded(Theme.CARD2, 14, density))
            stateListAnimator = null
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            setPadding(dp(14), dp(4), dp(14), dp(6))
            setOnClickListener { finish() }
        }
        header.addView(back)
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }.apply {
            addView(TextView(this@NodePickerActivity).apply {
                text = "选择线路"
                textSize = 18f
                setTextColor(Theme.TEXT)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@NodePickerActivity).apply {
                text = "点击来源分组测速 · 默认分组自动测"
                textSize = 11f
                setTextColor(Theme.SUBTEXT)
            })
        })
        bestBtn = Button(this).apply {
            text = "自动优选"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = Theme.ripple(Theme.gradient(Theme.GRADIENT_MAIN, 14, density))
            stateListAnimator = null
            minHeight = 0; minimumHeight = 0
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener { pickBest() }
        }
        header.addView(bestBtn)

        searchInput = EditText(this).apply {
            hint = "搜索线路名 / 地区"
            textSize = 13f
            setTextColor(Theme.TEXT)
            setHintTextColor(Theme.FAINT)
            background = Theme.card(12, density)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    query = s?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                    renderList()
                }
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(windowToken, 0)
                    true
                } else false
            }
        }

        countView = TextView(this).apply {
            textSize = 11f
            setTextColor(Theme.SUBTEXT)
            setPadding(0, dp(8), 0, dp(2))
        }

        chipBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipBar, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        hintView = TextView(this).apply {
            textSize = 11f
            setTextColor(Theme.SUBTEXT)
            visibility = View.GONE
            setPadding(0, dp(4), 0, 0)
        }

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(listContainer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        root.addView(header)
        root.addView(searchInput, topMargin(dp(10)))
        root.addView(countView)
        root.addView(chipScroll, topMargin(dp(4)))
        root.addView(hintView)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) }
        )
        return root
    }

    private fun topMargin(margin: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = margin }

    // ---------- 来源分组 ----------

    private fun groupList(): List<String> {
        val seen = linkedSetOf<String>()
        NodeStore.sources.forEach { seen.add(it) }
        return seen.toList()
    }

    private fun groupLabel(src: String): String =
        if (src == "?") "缓存" else src

    private fun renderChips() {
        chipBar.removeAllViews()
        val groups = groupList()
        if (groups.isEmpty()) return
        if (currentGroup == null || currentGroup !in groups) currentGroup = groups.first()
        for (g in groups) {
            val active = g == currentGroup
            val count = NodeStore.sources.count { it == g }
            val chip = Button(this).apply {
                text = "${groupLabel(g)} $count"
                isAllCaps = false
                textSize = 12f
                stateListAnimator = null
                minHeight = 0; minimumHeight = 0
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setTextColor(if (active) Color.WHITE else Theme.SUBTEXT)
                typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = Theme.ripple(
                    if (active) Theme.gradient(Theme.GRADIENT_MAIN, 18, density)
                    else Theme.card(18, density)
                )
                setOnClickListener {
                    currentGroup = g
                    renderChips()
                    renderList()
                    updateHint()
                    // 点击分组 = 按需测速:该分组没有结果时立即测
                    if (!LatencyEngine.hasResultsForSource(g) && !LatencyEngine.isRunning()) {
                        LatencyEngine.ensureForSource(g)
                        updateHint()
                    }
                }
            }
            chipBar.addView(
                chip,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { rightMargin = dp(8) }
            )
        }
    }

    private fun updateHint() {
        val g = currentGroup ?: return
        hintView.visibility = View.VISIBLE
        hintView.text = when {
            LatencyEngine.isTestingSource(g) -> "正在测速「${groupLabel(g)}」…"
            !LatencyEngine.hasResultsForSource(g) && !LatencyEngine.isRunning() -> "点击上方「${groupLabel(g)}」开始测速"
            LatencyEngine.isRunning() -> "正在测速…(${latencies.size} 条已完成)"
            else -> "测速完成(${LatencyEngine.sourceLabel()}) · 共 ${latencies.size} 条已测"
        }
    }

    // ---------- 地区 ----------

    private fun regionFor(index: Int, link: Link): String =
        regionCache.getOrPut(index) {
            RegionResolver.regionOf(this, SingBoxConfig.hostOf(link.uri) ?: "", link.name).region
        }

    // ---------- 列表 ----------

    private fun visibleLinks(): List<Int> =
        NodeStore.links.indices
            .filter { i -> NodeStore.sources.getOrNull(i) == currentGroup }
            .filter { query.isEmpty() || matches(it) }
            .sortedBy { latencyOf(tagOf(it)) ?: Int.MAX_VALUE }

    private fun matches(index: Int): Boolean {
        val l = NodeStore.links[index]
        return l.name.lowercase(Locale.ROOT).contains(query) ||
            regionFor(index, l).lowercase(Locale.ROOT).contains(query) ||
            l.uri.lowercase(Locale.ROOT).contains(query)
    }

    private fun latencyOf(tag: String): Int? = latencies[tag]

    private fun tagOf(index: Int): String = NodeStore.tags.getOrNull(index) ?: ""

    private fun renderList() {
        lastRenderedCount = NodeStore.links.size
        countView.text = "${NodeStore.links.size} 条线路 · ${latencies.size} 条已测速"
        listContainer.removeAllViews()
        if (NodeStore.links.isEmpty()) {
            listContainer.addView(emptyView(if (JiguangVpnService.running || isLoadingAnywhere()) "正在获取线路…" else "暂无线路,请在首页刷新订阅"))
            return
        }
        val visible = visibleLinks()
        if (visible.isEmpty()) {
            listContainer.addView(emptyView("该分组没有匹配的线路"))
            return
        }
        for (index in visible) {
            val row = nodeRow(index, NodeStore.links[index])
            row.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            row.setOnClickListener {
                NodeStore.selectedIndex = index
                toast("已选择:${NodeStore.links[index].name}")
                finish()
            }
            listContainer.addView(row)
        }
    }

    private fun isLoadingAnywhere(): Boolean = LatencyEngine.isRunning()

    private fun emptyView(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Theme.SUBTEXT)
        gravity = Gravity.CENTER
        setPadding(0, dp(36), 0, dp(36))
    }

    @SuppressLint("SetTextI18n")
    private fun nodeRow(index: Int, link: Link): LinearLayout {
        val region = regionFor(index, link)
        val selected = index == NodeStore.selectedIndex
        val latency = latencyOf(tagOf(index))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Theme.ripple(
                if (selected) {
                    Theme.strokeRounded(Theme.SELECTED_BG, 16, 1, Theme.PRIMARY, density)
                } else {
                    Theme.card(16, density)
                }
            )
            setPadding(dp(13), dp(12), dp(13), dp(12))
        }
        row.addView(TextView(this).apply {
            text = region
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = Theme.rounded(regionBadgeColor(region), 10, density)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        mid.addView(TextView(this).apply {
            text = link.name
            textSize = 14f
            setTextColor(Theme.TEXT)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        mid.addView(TextView(this).apply {
            text = protocolOf(link.uri).uppercase(Locale.ROOT)
            textSize = 11f
            setTextColor(Theme.FAINT)
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(mid)
        row.addView(TextView(this).apply {
            text = when {
                selected -> "使用中"
                latency != null && latency >= LatencyTester.TIMEOUT_MS -> "超时"
                latency != null && latency >= LatencyTester.UNTESTED_MS -> "待测"
                latency != null -> delayLabel(latency)
                LatencyEngine.isTestingSource(currentGroup ?: "") -> "…"
                else -> "未测"
            }
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            when {
                selected -> {
                    setTextColor(Theme.PRIMARY)
                    background = Theme.rounded(Theme.CARD2, 10, density)
                }
                latency != null && latency >= LatencyTester.UNTESTED_MS -> setTextColor(Theme.FAINT)
                latency != null -> {
                    setTextColor(delayColor(latency))
                    background = Theme.rounded(Theme.CARD2, 10, density)
                }
                else -> setTextColor(Theme.FAINT)
            }
            setPadding(dp(10), dp(5), dp(10), dp(5))
        })
        return row
    }

    private fun regionBadgeColor(region: String): Int = when (region) {
        "香港" -> Color.rgb(0x24, 0x4C, 0x7A)
        "台湾" -> Color.rgb(0x2A, 0x5A, 0x4A)
        "日本" -> Color.rgb(0x4A, 0x2A, 0x5A)
        "新加坡" -> Color.rgb(0x5A, 0x3A, 0x24)
        "美国" -> Color.rgb(0x2A, 0x3A, 0x6A)
        "韩国" -> Color.rgb(0x2A, 0x5A, 0x6A)
        "英国", "德国", "法国", "荷兰" -> Color.rgb(0x3A, 0x2A, 0x4A)
        "未知", "其他" -> Theme.BADGE
        else -> Theme.BADGE
    }

    private fun delayColor(ms: Int): Int = when {
        ms < 150 -> Theme.GREEN
        ms < 400 -> Theme.YELLOW
        else -> Theme.RED
    }

    private fun delayLabel(ms: Int): String = if (ms >= 9999) "超时" else "$ms ms"

    private fun protocolOf(uri: String) = uri.substringBefore("://", "unknown").lowercase()

    // ---------- 优选 ----------

    private fun pickBest() {
        // 只在已确认可用(内核实测或本地探测通过)的节点中优选,超时/待测不参与
        val best = latencies.entries
            .filter { it.value < LatencyTester.UNTESTED_MS }
            .minByOrNull { it.value }
        if (best == null) {
            toast("暂无可用测速结果,请先测速")
            return
        }
        val idx = NodeStore.tags.indexOf(best.key)
        if (idx < 0) {
            toast("节点已失效,请刷新订阅")
            return
        }
        NodeStore.selectedIndex = idx
        toast("已优选:${NodeStore.links[idx].name}(${best.value} ms)")
        finish()
    }

    private fun resolveRegions() {
        if (NodeStore.isEmpty) return
        lifecycleScope.launch {
            val hosts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                NodeStore.links.map { SingBoxConfig.hostOf(it.uri) ?: "" }.filter { it.isNotEmpty() }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                RegionResolver.refreshUnknownOnline(this@NodePickerActivity, hosts)
            }
            runOnUiThread {
                regionCache.clear()
                renderList()
            }
        }
    }

    private fun dp(value: Int): Int = Theme.dp(value, density)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

/**
 * 极简的 NodeStore 变更监听(仅用于本页流式刷新),避免引入额外依赖。
 */
object NodeStoreBridge {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun notifyChanged() {
        for (l in listeners) l()
    }
}
