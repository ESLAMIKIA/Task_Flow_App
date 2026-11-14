package com.example.taskflow

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.random.Random

// --------------------------------------------------
// COLORS
// --------------------------------------------------

private val Background = Color(0xFF171525)
private val CardColor = Color(0xFF29243C)
private val CardLight = Color(0xFF342D4B)
private val Purple = Color(0xFF906FFF)
private val Mint = Color(0xFF36D6A0)
private val Orange = Color(0xFFFFB34D)
private val Pink = Color(0xFFFF668B)
private val Blue = Color(0xFF42C9E8)
private val TextWhite = Color(0xFFF8F6FF)
private val TextGray = Color(0xFFAAA4BD)

// --------------------------------------------------
// DATA
// --------------------------------------------------

enum class TaskType {
    DAILY,
    LONG_TERM
}

data class IconOption(
    val emoji: String,
    val name: String,
    val color: String
)

private val iconOptions = listOf(
    IconOption("🏋️", "Fitness", "#FFB34D"),
    IconOption("📚", "Reading", "#FF668B"),
    IconOption("💻", "Coding", "#42C9E8"),
    IconOption("🌍", "Language", "#906FFF"),
    IconOption("💼", "Work", "#36D6A0"),
    IconOption("🎯", "Goals", "#FF8C55"),
    IconOption("🧠", "Learning", "#B58AFF"),
    IconOption("❤️", "Health", "#FF668B"),
    IconOption("✈️", "Travel", "#42C9E8"),
    IconOption("⭐", "Other", "#FFD166")
)

data class TaskItem(
    val id: Long,
    val title: String,
    val type: TaskType,
    val icon: String,
    val color: String,
    val deadline: String?,
    val completedDates: Set<String> = emptySet(),
    val isCompleted: Boolean = false,
    val createdDate: String = LocalDate.now().toString()
)

private fun TaskItem.colorValue(): Color {
    return try {
        Color(android.graphics.Color.parseColor(color))
    } catch (e: Exception) {
        Purple
    }
}

private val dateFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)

private fun LocalDate.toKey(): String = toString()

// --------------------------------------------------
// STORAGE
// --------------------------------------------------

private fun saveTasks(
    context: Context,
    tasks: List<TaskItem>
) {
    val array = JSONArray()

    tasks.forEach { task ->
        val obj = JSONObject()

        obj.put("id", task.id)
        obj.put("title", task.title)
        obj.put("type", task.type.name)
        obj.put("icon", task.icon)
        obj.put("color", task.color)
        obj.put("deadline", task.deadline ?: JSONObject.NULL)
        obj.put("isCompleted", task.isCompleted)
        obj.put("createdDate", task.createdDate)

        val dates = JSONArray()
        task.completedDates.forEach { dates.put(it) }

        obj.put("completedDates", dates)
        array.put(obj)
    }

    context.getSharedPreferences(
        "taskflow_storage",
        Context.MODE_PRIVATE
    ).edit().putString("tasks", array.toString()).apply()
}

private fun loadTasks(context: Context): List<TaskItem> {
    return try {
        val json = context.getSharedPreferences(
            "taskflow_storage",
            Context.MODE_PRIVATE
        ).getString("tasks", "[]") ?: "[]"

        val array = JSONArray(json)
        val result = mutableListOf<TaskItem>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            val dates = obj.optJSONArray("completedDates")
            val completedDates = mutableSetOf<String>()

            if (dates != null) {
                for (j in 0 until dates.length()) {
                    completedDates.add(dates.getString(j))
                }
            }

            val deadlineValue = obj.opt("deadline")
            val deadline = if (
                deadlineValue == null ||
                deadlineValue == JSONObject.NULL
            ) null else deadlineValue.toString()

            val createdDate = obj.optString(
                "createdDate",
                completedDates.minOrNull() ?: LocalDate.now().toString()
            )

            result.add(
                TaskItem(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    type = TaskType.valueOf(obj.getString("type")),
                    icon = obj.getString("icon"),
                    color = obj.getString("color"),
                    deadline = deadline,
                    completedDates = completedDates,
                    isCompleted = obj.optBoolean("isCompleted", false),
                    createdDate = createdDate
                )
            )
        }

        result
    } catch (e: Exception) {
        emptyList()
    }
}

// --------------------------------------------------
// STREAK + XP CALCULATIONS
// --------------------------------------------------

private fun calculateDailyStreak(
    tasks: List<TaskItem>,
    today: LocalDate = LocalDate.now()
): Int {
    val completedDays = tasks
        .filter { it.type == TaskType.DAILY }
        .flatMap { it.completedDates }
        .mapNotNull { value ->
            try {
                LocalDate.parse(value)
            } catch (_: Exception) {
                null
            }
        }
        .toSet()

    var cursor = when {
        today in completedDays -> today
        today.minusDays(1) in completedDays -> today.minusDays(1)
        else -> return 0
    }

    var streak = 0
    while (cursor in completedDays) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

private fun calculateTotalXp(tasks: List<TaskItem>): Int {
    val dailyXp = tasks
        .filter { it.type == TaskType.DAILY }
        .sumOf { it.completedDates.size * 10 }

    val longTermXp = tasks
        .filter { it.type == TaskType.LONG_TERM && it.isCompleted }
        .sumOf { 25 }

    return dailyXp + longTermXp
}

// --------------------------------------------------
// ACTIVITY
// --------------------------------------------------

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor =
            android.graphics.Color.rgb(23, 21, 37)

        window.navigationBarColor =
            android.graphics.Color.rgb(23, 21, 37)

        window.decorView.systemUiVisibility = 0

        setContent {
            var showSplash by remember { mutableStateOf(true) }

            if (showSplash) {
                SplashScreen {
                    showSplash = false
                }
            } else {
                TaskFlowApp()
            }
        }
    }
}

// --------------------------------------------------
// MAIN APP
// --------------------------------------------------

@Composable
fun TaskFlowApp() {

    val context = LocalContext.current

    val tasks = remember {
        mutableStateListOf<TaskItem>().apply {
            addAll(loadTasks(context))
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    var celebrationTrigger by remember { mutableIntStateOf(0) }

    val totalXp = calculateTotalXp(tasks)
    val currentLevel = totalXp / 100
    val xpInCurrentLevel = totalXp % 100
    val dailyStreak = calculateDailyStreak(tasks)

    fun persist() {
        saveTasks(context, tasks)
    }

    fun addTask(task: TaskItem) {
        tasks.add(task)
        persist()
        selectedTab = if (task.type == TaskType.DAILY) 0 else 2
    }

    fun toggleTask(task: TaskItem, date: LocalDate) {
        val index = tasks.indexOfFirst { it.id == task.id }

        if (index == -1) return

        val old = tasks[index]

        tasks[index] = if (old.type == TaskType.DAILY) {
            val key = date.toKey()

            val newDates = old.completedDates.toMutableSet()

            if (key in newDates) {
                newDates.remove(key)
            } else {
                newDates.add(key)
                celebrationTrigger++
            }

            old.copy(completedDates = newDates)
        } else {
            val newValue = !old.isCompleted

            if (newValue) celebrationTrigger++

            old.copy(isCompleted = newValue)
        }

        persist()
    }

    fun deleteTask(task: TaskItem) {
        tasks.removeAll { it.id == task.id }
        persist()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            secondary = Mint,
            background = Background,
            surface = CardColor,
            onBackground = TextWhite,
            onSurface = TextWhite
        )
    ) {

        Scaffold(
            containerColor = Background,
            bottomBar = {
                TaskBottomBar(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }
        ) { padding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(tween(250)) togetherWith
                                fadeOut(tween(150))
                    },
                    label = "MainTabs"
                ) { tab ->

                    when (tab) {
                        0 -> DailyScreen(
                            tasks = tasks,
                            selectedDate = selectedDate,
                            streak = dailyStreak,
                            level = currentLevel,
                            totalXp = totalXp,
                            xpInCurrentLevel = xpInCurrentLevel,
                            onDateSelected = { selectedDate = it },
                            onToggle = { task ->
                                toggleTask(task, selectedDate)
                            },
                            onDelete = ::deleteTask
                        )

                        1 -> AddScreen(
                            onCreate = ::addTask
                        )

                        else -> LongTermScreen(
                            tasks = tasks,
                            onToggle = { task ->
                                toggleTask(task, selectedDate)
                            },
                            onDelete = ::deleteTask
                        )
                    }
                }

                ConfettiOverlay(
                    trigger = celebrationTrigger
                )
            }
        }
    }
}

// --------------------------------------------------
// BOTTOM NAVIGATION
// --------------------------------------------------

@Composable
fun TaskBottomBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(85.dp)
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val w = size.width
            val h = size.height
            val center = w / 2

            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(center - 80.dp.toPx(), 0f)

                quadraticTo(
                    center - 48.dp.toPx(),
                    0f,
                    center - 38.dp.toPx(),
                    20.dp.toPx()
                )

                quadraticTo(
                    center,
                    65.dp.toPx(),
                    center + 38.dp.toPx(),
                    20.dp.toPx()
                )

                quadraticTo(
                    center + 48.dp.toPx(),
                    0f,
                    center + 80.dp.toPx(),
                    0f
                )

                lineTo(w, 0f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }

            drawPath(path, CardColor)
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 19.dp),
            verticalAlignment = Alignment.Top
        ) {

            NavigationItem(
                modifier = Modifier.weight(1f),
                emoji = "☀️",
                title = "Daily",
                selected = selectedTab == 0
            ) {
                onSelect(0)
            }

            Spacer(Modifier.weight(1f))

            NavigationItem(
                modifier = Modifier.weight(1f),
                emoji = "🎯",
                title = "Long-term",
                selected = selectedTab == 2
            ) {
                onSelect(2)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-17).dp)
                .size(67.dp)
                .shadow(
                    elevation = 15.dp,
                    shape = CircleShape,
                    ambientColor = Purple,
                    spotColor = Purple
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFAD96FF),
                            Purple,
                            Color(0xFF5938C6)
                        )
                    ),
                    CircleShape
                )
                .border(
                    2.dp,
                    Color.White.copy(alpha = 0.2f),
                    CircleShape
                )
                .clickable { onSelect(1) },
            contentAlignment = Alignment.Center
        ) {

            Text(
                "+",
                color = Color.White,
                fontSize = 39.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

@Composable
fun NavigationItem(
    modifier: Modifier,
    emoji: String,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            emoji,
            fontSize = 20.sp
        )

        Text(
            title,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold
            else FontWeight.Normal,
            color = if (selected) Purple else TextGray
        )
    }
}

// --------------------------------------------------
// DAILY SCREEN
// --------------------------------------------------

@Composable
fun DailyScreen(
    tasks: List<TaskItem>,
    selectedDate: LocalDate,
    streak: Int,
    level: Int,
    totalXp: Int,
    xpInCurrentLevel: Int,
    onDateSelected: (LocalDate) -> Unit,
    onToggle: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit
) {

    var showMonth by remember { mutableStateOf(false) }

    val dailyTasks = tasks.filter {
        it.type == TaskType.DAILY &&
                runCatching { LocalDate.parse(it.createdDate) <= selectedDate }
                    .getOrDefault(true)
    }

    val completed = dailyTasks.count {
        selectedDate.toKey() in it.completedDates
    }

    val total = dailyTasks.size

    val progress = if (total == 0) 0f
    else completed.toFloat() / total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 25.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column {
                Text(
                    "TASKFLOW",
                    color = Purple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "Let's make today count!",
                    fontSize = 21.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text("🔥 $streak", fontSize = 21.sp)
                Text(
                    "DAY STREAK",
                    color = Orange,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        XPCard(
            level = level,
            totalXp = totalXp,
            xpInCurrentLevel = xpInCurrentLevel
        )

        Spacer(Modifier.height(25.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column {
                Text(
                    "Your Schedule",
                    fontSize = 19.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    selectedDate.format(
                        DateTimeFormatter.ofPattern(
                            "EEEE, MMMM dd",
                            Locale.ENGLISH
                        )
                    ),
                    fontSize = 12.sp,
                    color = TextGray
                )
            }

            TextButton(
                onClick = { showMonth = !showMonth }
            ) {
                Text(
                    if (showMonth) "WEEK" else "MONTH",
                    color = Purple,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (showMonth) {
            MonthCalendar(
                selectedDate = selectedDate,
                onDateSelected = onDateSelected
            )
        } else {
            WeekCalendar(
                selectedDate = selectedDate,
                onDateSelected = onDateSelected
            )
        }

        Spacer(Modifier.height(22.dp))

        ProgressCard(
            completed = completed,
            total = total,
            progress = progress
        )

        Spacer(Modifier.height(25.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Daily Activities",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            Text(
                "$completed / $total",
                fontSize = 12.sp,
                color = Mint
            )
        }

        Spacer(Modifier.height(12.dp))

        if (dailyTasks.isEmpty()) {
            EmptyState(
                emoji = "✨",
                title = "Your day starts here",
                subtitle = "Tap the purple + button to create your first task."
            )
        }

        dailyTasks.forEach { task ->
            TaskCard(
                task = task,
                selectedDate = selectedDate,
                onToggle = { onToggle(task) },
                onDelete = { onDelete(task) }
            )

            Spacer(Modifier.height(10.dp))
        }
    }
}

// --------------------------------------------------
// XP CARD
// --------------------------------------------------

@Composable
fun XPCard(
    level: Int,
    totalXp: Int,
    xpInCurrentLevel: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF7557E8),
                        Color(0xFF9D73F7),
                        Color(0xFF6549CA)
                    )
                )
            )
            .padding(19.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "YOUR JOURNEY",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    "Level $level",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text("🏆", fontSize = 39.sp)
        }

        Spacer(Modifier.height(13.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(xpInCurrentLevel / 100f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Orange)
            )
        }

        Spacer(Modifier.height(7.dp))

        Text(
            "$xpInCurrentLevel / 100 XP  •  $totalXp XP total",
            color = Color.White,
            fontSize = 11.sp
        )
    }
}

@Composable
fun WeekCalendar(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {

    val start = selectedDate.minusDays(
        (selectedDate.dayOfWeek.value - 1).toLong()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {

        repeat(7) { index ->

            val date = start.plusDays(index.toLong())
            val selected = date == selectedDate

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (selected) Purple else CardColor
                    )
                    .clickable {
                        onDateSelected(date)
                    }
                    .padding(vertical = 13.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    date.dayOfWeek.getDisplayName(
                        TextStyle.SHORT,
                        Locale.ENGLISH
                    ).take(1),
                    fontSize = 11.sp,
                    color = if (selected) Color.White
                    else TextGray
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// --------------------------------------------------
// MONTH CALENDAR
// --------------------------------------------------

@Composable
fun MonthCalendar(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {

    var visibleMonth by remember(selectedDate) {
        mutableStateOf(YearMonth.from(selectedDate))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardColor)
            .padding(14.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                visibleMonth.month.getDisplayName(
                    TextStyle.FULL,
                    Locale.ENGLISH
                ) + " ${visibleMonth.year}",
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                fontSize = 16.sp
            )

            Row {
                TextButton(
                    onClick = {
                        visibleMonth = visibleMonth.minusMonths(1)
                    }
                ) {
                    Text("‹", color = Purple, fontSize = 23.sp)
                }

                TextButton(
                    onClick = {
                        visibleMonth = visibleMonth.plusMonths(1)
                    }
                ) {
                    Text("›", color = Purple, fontSize = 23.sp)
                }
            }
        }

        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = TextGray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(9.dp))

        val offset = visibleMonth.atDay(1).dayOfWeek.value - 1
        val days = visibleMonth.lengthOfMonth()
        val rows = (offset + days + 6) / 7

        repeat(rows) { week ->

            Row {
                repeat(7) { day ->

                    val number = week * 7 + day - offset + 1

                    val valid = number in 1..days

                    val date = if (valid) {
                        visibleMonth.atDay(number)
                    } else null

                    val selected = date == selectedDate

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) Purple
                                else Color.Transparent
                            )
                            .clickable {
                                if (date != null) {
                                    onDateSelected(date)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {

                        if (valid) {
                            Text(
                                number.toString(),
                                fontSize = 12.sp,
                                color = if (selected) Color.White
                                else TextWhite
                            )
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------
// PROGRESS CARD
// --------------------------------------------------

@Composable
fun ProgressCard(
    completed: Int,
    total: Int,
    progress: Float
) {

    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(700),
        label = "DailyProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardColor)
            .padding(17.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                "Daily Progress",
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            Text(
                "${(progress * 100).toInt()}%",
                color = Mint,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(13.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(CardLight)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Mint, Color(0xFF80F2C9))
                        )
                    )
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "$completed of $total tasks completed. Keep going!",
            color = TextGray,
            fontSize = 11.sp
        )
    }
}

// --------------------------------------------------
// TASK CARD
// --------------------------------------------------

@Composable
fun TaskCard(
    task: TaskItem,
    selectedDate: LocalDate,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val done = if (task.type == TaskType.DAILY) {
        selectedDate.toKey() in task.completedDates
    } else {
        task.isCompleted
    }

    val color = task.colorValue()
    var showDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = color.copy(alpha = 0.12f),
                spotColor = color.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(color.copy(alpha = 0.13f), CardColor, CardColor)
                )
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = if (done) 0.35f else 0.18f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 13.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(47.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(color.copy(alpha = 0.18f))
                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(task.icon, fontSize = 24.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                color = if (done) TextGray else TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
            )

            if (task.deadline != null) {
                val date = try {
                    LocalDate.parse(task.deadline)
                } catch (e: Exception) {
                    null
                }

                if (date != null) {
                    val daysLeft = java.time.temporal.ChronoUnit.DAYS
                        .between(LocalDate.now(), date)

                    val deadlineColor = when {
                        daysLeft < 0 -> Pink
                        daysLeft <= 3 -> Orange
                        else -> TextGray
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            daysLeft < 0 -> "Overdue"
                            daysLeft == 0L -> "Due today"
                            else -> "$daysLeft days left"
                        },
                        fontSize = 10.sp,
                        color = deadlineColor
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Checkbox(
            checked = done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = color,
                uncheckedColor = TextGray,
                checkmarkColor = Background
            )
        )

        Spacer(Modifier.width(5.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Pink.copy(alpha = 0.12f))
                .clickable { showDelete = true },
            contentAlignment = Alignment.Center
        ) {
            Text("🗑️", fontSize = 16.sp)
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete task?") },
            text = { Text("Do you want to delete ${task.title}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDelete = false
                    }
                ) {
                    Text("Delete", color = Pink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Cancel")
                }
            },
            containerColor = CardColor
        )
    }
}

// --------------------------------------------------
// ADD SCREEN
// --------------------------------------------------

@Composable
fun AddScreen(
    onCreate: (TaskItem) -> Unit
) {

    var taskType by remember { mutableStateOf(TaskType.DAILY) }
    var selectedIcon by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf<LocalDate?>(null) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {

        Text(
            "CREATE TASK",
            color = Purple,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Make it happen!",
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextWhite
        )

        Spacer(Modifier.height(26.dp))

        FormLabel("01  ·  CHOOSE TASK TYPE")

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            TypeCard(
                modifier = Modifier.weight(1f),
                title = "Daily",
                emoji = "🔁",
                description = "Repeats daily",
                selected = taskType == TaskType.DAILY,
                color = Purple
            ) {
                taskType = TaskType.DAILY
            }

            TypeCard(
                modifier = Modifier.weight(1f),
                title = "Long-term",
                emoji = "🎯",
                description = "One-time goal",
                selected = taskType == TaskType.LONG_TERM,
                color = Mint
            ) {
                taskType = TaskType.LONG_TERM
            }
        }

        Spacer(Modifier.height(27.dp))

        FormLabel("02  ·  CHOOSE AN ICON")

        Spacer(Modifier.height(12.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            iconOptions.chunked(5).forEach { rowItems ->

                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {

                    rowItems.forEach { option ->

                        val index = iconOptions.indexOf(option)
                        val selected = selectedIcon == index

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    if (selected) CardLight
                                    else CardColor
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected)
                                        Color(android.graphics.Color.parseColor(option.color))
                                    else Color.Transparent,
                                    shape = RoundedCornerShape(13.dp)
                                )
                                .clickable { selectedIcon = index }
                                .padding(vertical = 11.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Text(
                                option.emoji,
                                fontSize = 22.sp
                            )

                            Spacer(Modifier.height(3.dp))

                            Text(
                                option.name,
                                color = if (selected) TextWhite else TextGray,
                                fontSize = 9.sp
                            )
                        }
                    }

                    repeat(5 - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(27.dp))

        FormLabel("03  ·  TASK NAME")

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("What do you want to achieve?")
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple,
                unfocusedBorderColor = CardLight,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                cursorColor = Purple,
                focusedContainerColor = CardColor,
                unfocusedContainerColor = CardColor
            )
        )

        Spacer(Modifier.height(27.dp))

        FormLabel("04  ·  DEADLINE")

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardColor)
                .clickable {
                    val initial = deadline ?: LocalDate.now()

                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            deadline = LocalDate.of(
                                year,
                                month + 1,
                                day
                            )
                        },
                        initial.year,
                        initial.monthValue - 1,
                        initial.dayOfMonth
                    ).show()
                }
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text("📅", fontSize = 22.sp)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    deadline?.format(dateFormatter) ?: "Choose a deadline",
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "Gregorian calendar",
                    color = TextGray,
                    fontSize = 10.sp
                )
            }

            Text(
                "CHANGE",
                color = Purple,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (deadline != null) {
            TextButton(
                onClick = { deadline = null }
            ) {
                Text("Remove deadline", color = Pink)
            }
        }

        Spacer(Modifier.height(30.dp))

        Button(
            onClick = {
                if (title.isNotBlank()) {
                    val option = iconOptions[selectedIcon]

                    onCreate(
                        TaskItem(
                            id = System.currentTimeMillis(),
                            title = title.trim(),
                            type = taskType,
                            icon = option.emoji,
                            color = option.color,
                            deadline = deadline?.toString()
                        )
                    )
                }
            },
            enabled = title.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(
                    elevation = 9.dp,
                    shape = RoundedCornerShape(17.dp),
                    spotColor = Purple
                ),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Purple,
                disabledContainerColor = CardLight
            )
        ) {

            Text(
                "CREATE TASK  ✨",
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

// --------------------------------------------------
// FORM COMPONENTS
// --------------------------------------------------

@Composable
fun FormLabel(text: String) {
    Text(
        text,
        color = TextGray,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
fun TypeCard(
    modifier: Modifier,
    title: String,
    emoji: String,
    description: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) color.copy(alpha = 0.16f)
                else CardColor
            )
            .border(
                1.5.dp,
                if (selected) color else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {

        Text(emoji, fontSize = 27.sp)

        Spacer(Modifier.height(10.dp))

        Text(
            title,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )

        Text(
            description,
            color = TextGray,
            fontSize = 10.sp
        )
    }
}

// --------------------------------------------------
// LONG TERM SCREEN
// --------------------------------------------------

@Composable
fun LongTermScreen(
    tasks: List<TaskItem>,
    onToggle: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit
) {

    val longTasks = tasks.filter {
        it.type == TaskType.LONG_TERM
    }

    val completed = longTasks.count { it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {

        Text(
            "YOUR BIG PICTURE",
            color = Purple,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(5.dp))

        Text(
            "Big goals, big dreams.",
            color = TextWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3A315E), Color(0xFF242B48))
                    )
                )
                .padding(19.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column {
                Text(
                    "GOALS COMPLETED",
                    color = TextGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "$completed / ${longTasks.size}",
                    color = TextWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Text("🏆", fontSize = 40.sp)
        }

        Spacer(Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Long-term Goals",
                color = TextWhite,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "${longTasks.size} GOALS",
                color = Purple,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(13.dp))

        if (longTasks.isEmpty()) {
            EmptyState(
                emoji = "🎯",
                title = "Dream bigger!",
                subtitle = "Create your first long-term goal with the + button."
            )
        }

        longTasks.forEach { task ->

            TaskCard(
                task = task,
                selectedDate = LocalDate.now(),
                onToggle = { onToggle(task) },
                onDelete = { onDelete(task) }
            )

            Spacer(Modifier.height(10.dp))
        }
    }
}

// --------------------------------------------------
// EMPTY STATE
// --------------------------------------------------

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardColor)
            .padding(25.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(emoji, fontSize = 39.sp)

        Spacer(Modifier.height(10.dp))

        Text(
            title,
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Spacer(Modifier.height(5.dp))

        Text(
            subtitle,
            color = TextGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

// --------------------------------------------------
// CONFETTI ANIMATION
// --------------------------------------------------

@Composable
fun ConfettiOverlay(
    trigger: Int
) {

    val progress = remember { Animatable(1f) }

    val particles = remember {
        List(65) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextInt(4, 10).toFloat(),
                color = listOf(
                    Purple,
                    Mint,
                    Orange,
                    Pink,
                    Blue,
                    Color.White
                ).random()
            )
        }
    }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = 1100,
                    easing = LinearOutSlowInEasing
                )
            )
        }
    }

    if (progress.value < 1f) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val p = progress.value

            particles.forEachIndexed { index, particle ->

                val startX = particle.x * size.width
                val startY = particle.y * size.height * 0.3f

                val x = startX +
                        kotlin.math.sin(
                            (p * 8 + index).toDouble()
                        ).toFloat() * 30f

                val y = startY + p * size.height * 0.7f

                val alpha = (1f - p).coerceIn(0f, 1f)

                drawCircle(
                    color = particle.color.copy(alpha = alpha),
                    radius = particle.size,
                    center = androidx.compose.ui.geometry.Offset(x, y),
                    style = Fill
                )
            }
        }
    }
}

data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color
)

@Composable
fun SplashScreen(onFinished: () -> Unit) {

    var started by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.4f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 100f
        ),
        label = "LogoScale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(800),
        label = "LogoAlpha"
    )

    val titleOffset by animateFloatAsState(
        targetValue = if (started) 0f else 35f,
        animationSpec = tween(850, delayMillis = 250),
        label = "TitleOffset"
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(850, delayMillis = 250),
        label = "TitleAlpha"
    )

    val creatorAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(900, delayMillis = 900),
        label = "CreatorAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(
        label = "SplashAnimation"
    )

    val glow by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Glow"
    )

    LaunchedEffect(Unit) {
        started = true
        kotlinx.coroutines.delay(3200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF292044),
                        Color(0xFF171525),
                        Color(0xFF100F1D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        // Background glow
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Purple.copy(alpha = glow),
                            Purple.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        // Main content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {

            // Animated logo
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    }
                    .shadow(
                        elevation = 25.dp,
                        shape = RoundedCornerShape(32.dp),
                        ambientColor = Purple,
                        spotColor = Purple
                    )
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFB9A4FF),
                                Color(0xFF906FFF),
                                Color(0xFF5938C6)
                            )
                        ),
                        RoundedCornerShape(32.dp)
                    )
                    .border(
                        1.5.dp,
                        Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    fontSize = 65.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(32.dp))

            // App name
            Text(
                text = "TASKFLOW",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 5.sp,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    translationY = titleOffset
                    alpha = titleAlpha
                }
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Make Every Day Count",
                fontSize = 14.sp,
                letterSpacing = 1.sp,
                color = TextGray,
                modifier = Modifier.graphicsLayer {
                    translationY = titleOffset
                    alpha = titleAlpha
                }
            )
        }

        // Creator credit
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 55.dp)
                .graphicsLayer {
                    alpha = creatorAlpha
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .width(35.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Purple)
            )

            Spacer(Modifier.height(17.dp))

            Text(
                text = "CREATED BY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                color = TextGray
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Amir Erfan Eslamikia",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }
    }
}
