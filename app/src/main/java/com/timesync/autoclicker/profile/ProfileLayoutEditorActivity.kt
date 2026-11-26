package com.timesync.autoclicker.profile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class ProfileLayoutEditorActivity : Activity() {
    private lateinit var repository: ApplicationProfileRepository
    private lateinit var profile: ApplicationProfile
    private lateinit var canvasView: LayoutCanvas
    private lateinit var actionList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ApplicationProfileRepository(this)
        val id = intent.getStringExtra(ApplicationProfilesActivity.EXTRA_PROFILE_ID)
        profile = id?.let { repository.get(it) } ?: run { finish(); return }
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        root.addView(TextView(this).apply {
            text = "LAYOUT • " + profile.name
            setTextColor(Color.WHITE)
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = "Ketuk area preview untuk menambah titik. Geser titik untuk mengubah koordinat. Maksimal 10 action."
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(0, dp(6), 0, dp(10))
        })
        canvasView = LayoutCanvas(profile.layout.actions) { actions -> updateActions(actions) }
        val displayWidthPx = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val displayHeightPx = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val previewWidthDp = (displayWidthPx / resources.displayMetrics.density).toInt() - 32
        val previewHeightDp = (previewWidthDp * displayHeightPx.toFloat() / displayWidthPx).toInt()
        root.addView(canvasView, LinearLayout.LayoutParams(-1, dp(previewHeightDp.coerceAtLeast(240))))
        root.addView(TextView(this).apply {
            text = "DAFTAR ACTION"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, dp(12), 0, dp(4))
        })
        val scroll = ScrollView(this)
        actionList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(actionList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(170)))
        root.addView(Button(this).apply {
            text = "SIMPAN LAYOUT"
            setOnClickListener {
                val error = ProfileValidator.validate(profile)
                if (error != null) toast(error)
                else if (repository.save(profile)) {
                    toast("Layout tersimpan")
                    finish()
                } else toast("Gagal menyimpan layout")
            }
        })
        root.addView(Button(this).apply { text = "KEMBALI"; setOnClickListener { finish() } })
        val outerScroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(15, 23, 42))
            addView(root)
        }
        setContentView(outerScroll)
        renderActions()
    }

    private fun updateActions(actions: List<ActionPoint>) {
        profile = profile.copy(layout = profile.layout.copy(actions = actions))
        renderActions()
    }

    private fun renderActions() {
        canvasView.setActions(profile.layout.actions)
        actionList.removeAllViews()
        profile.layout.actions.sortedBy { it.order }.forEach { action ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply {
                text = action.order.toString() + ". " + action.name +
                    "  (" + "%.2f".format(action.x) + ", " + "%.2f".format(action.y) + ")"
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = android.view.Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(tinyButton("EDIT") { editAction(action) })
            row.addView(tinyButton("↑") { moveAction(action, -1) })
            row.addView(tinyButton("↓") { moveAction(action, 1) })
            row.addView(tinyButton("×") {
                updateActions(profile.layout.actions.filterNot { it.id == action.id }.mapIndexed { i, a -> a.copy(order = i + 1) })
            })
            actionList.addView(row)
        }
    }

    private fun editAction(action: ActionPoint) {
        val name = EditText(this).apply { setText(action.name); hint = "Nama action" }
        val delay = EditText(this).apply {
            setText(action.delayAfterMs.toString())
            hint = "Delay setelah action (100–5000 ms)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(name)
            addView(delay)
        }
        AlertDialog.Builder(this).setTitle("Edit action " + action.order).setView(content)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                val updated = profile.layout.actions.map {
                    if (it.id == action.id) it.copy(
                        name = name.text.toString().trim().ifBlank { "Action " + it.order },
                        delayAfterMs = delay.text.toString().toLongOrNull() ?: it.delayAfterMs
                    ) else it
                }
                updateActions(updated)
            }.show()
    }

    private fun moveAction(action: ActionPoint, delta: Int) {
        val sorted = profile.layout.actions.sortedBy { it.order }.toMutableList()
        val from = sorted.indexOfFirst { it.id == action.id }
        val to = (from + delta).coerceIn(0, sorted.lastIndex)
        if (from < 0 || from == to) return
        val item = sorted.removeAt(from)
        sorted.add(to, item)
        updateActions(sorted.mapIndexed { i, a -> a.copy(order = i + 1) })
    }

    private fun tinyButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 10f
        minWidth = dp(34)
        setPadding(0, 0, 0, 0)
        setOnClickListener { action() }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class LayoutCanvas(
        initial: List<ActionPoint>,
        private val changed: (List<ActionPoint>) -> Unit
    ) : View(this@ProfileLayoutEditorActivity) {
        private var actions = initial
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 41, 59) }
        private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(71, 85, 105); strokeWidth = dp(1).toFloat() }
        private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 204, 21); style = Paint.Style.FILL }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = dp(12).toFloat(); textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        private var selectedId: String? = null
        private var moved = false

        fun setActions(value: List<ActionPoint>) { actions = value; invalidate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), dp(12).toFloat(), dp(12).toFloat(), fill)
            for (i in 1..3) {
                val x = width * i / 4f
                val y = height * i / 4f
                canvas.drawLine(x, 0f, x, height.toFloat(), grid)
                canvas.drawLine(0f, y, width.toFloat(), y, grid)
            }
            actions.sortedBy { it.order }.forEach { a ->
                val x = a.x * width
                val y = a.y * height
                canvas.drawCircle(x, y, dp(17).toFloat(), marker)
                canvas.drawText(a.order.toString(), x, y + dp(4).toFloat(), label)
            }
            if (actions.isEmpty()) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; textSize = dp(13).toFloat(); textAlign = Paint.Align.CENTER }
                canvas.drawText("Ketuk untuk menambah titik aksi", width / 2f, height / 2f, p)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    moved = false
                    selectedId = actions.minByOrNull { a ->
                        val dx = a.x * width - event.x
                        val dy = a.y * height - event.y
                        dx * dx + dy * dy
                    }?.takeIf { a ->
                        val dx = a.x * width - event.x
                        val dy = a.y * height - event.y
                        dx * dx + dy * dy <= dp(28) * dp(28)
                    }?.id
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (selectedId != null) {
                        moved = true
                        actions = actions.map { a ->
                            if (a.id == selectedId) a.copy(
                                x = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                y = (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            ) else a
                        }
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (selectedId == null && !moved) {
                        if (actions.size >= ProfileValidator.MAX_ACTIONS) {
                            toast("Maksimal 10 action")
                        } else {
                            val next = actions.size + 1
                            actions = actions + ActionPoint(
                                id = UUID.randomUUID().toString(),
                                name = "Action " + next,
                                x = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                y = (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f),
                                order = next,
                                delayAfterMs = profile.timing.clickIntervalMs
                            )
                        }
                    }
                    selectedId = null
                    changed(actions)
                    return true
                }
            }
            return true
        }
    }
}
