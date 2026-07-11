package dev.teddy.bongard

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class Problem(
    val num: Int,
    val image: String,
    val designer: String,
    val left: String,
    val right: String,
    // BP#300 only: 12 animated box gifs (the puzzle is about motion)
    val boxes: List<String>? = null,
)

private fun loadProblems(context: Context): List<Problem> {
    val json = context.assets.open("problems.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(json)
    return List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        Problem(
            num = o.getInt("num"),
            image = o.getString("image"),
            designer = o.getString("designer"),
            left = o.getString("left"),
            right = o.getString("right"),
            boxes = o.optJSONArray("boxes")?.let { b -> List(b.length()) { b.getString(it) } },
        )
    }
}

@Composable
private fun rememberGifLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
}

private val invertFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insets = WindowInsetsControllerCompat(window, window.decorView)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent { BongardApp() }
    }
}

@Composable
fun BongardApp() {
    val context = LocalContext.current
    val problems = remember { loadProblems(context) }
    val prefs = remember { context.getSharedPreferences("bongard", Context.MODE_PRIVATE) }

    var inverted by rememberSaveable { mutableStateOf(prefs.getBoolean("inverted", true)) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var panelOnLeft by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = prefs.getInt("page", 0).coerceIn(0, problems.lastIndex)
    ) { problems.size }

    LaunchedEffect(pagerState, prefs) {
        snapshotFlow { pagerState.currentPage }.collect {
            prefs.edit().putInt("page", it).apply()
            // fresh problem, fresh eyes: tuck the info panel away
            panelOpen = false
        }
    }

    val colors = if (inverted) {
        darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            primary = Color(0xFF9ECBFF),
        )
    } else {
        lightColorScheme(
            background = Color.White,
            surface = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            primary = Color(0xFF1A66B3),
        )
    }

    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ProblemPage(
                    problem = problems[page],
                    pageIndex = page,
                    problems = problems,
                    pagerState = pagerState,
                    inverted = inverted,
                    onToggleInvert = {
                        inverted = !inverted
                        prefs.edit().putBoolean("inverted", inverted).apply()
                    },
                    panelOpen = panelOpen,
                    panelOnLeft = panelOnLeft,
                    onTap = { tappedLeft ->
                        if (panelOpen) {
                            panelOpen = false
                        } else {
                            panelOnLeft = tappedLeft
                            panelOpen = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProblemPage(
    problem: Problem,
    pageIndex: Int,
    problems: List<Problem>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    inverted: Boolean,
    onToggleInvert: () -> Unit,
    panelOpen: Boolean,
    panelOnLeft: Boolean,
    onTap: (tappedLeft: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, problem.image) {
        value = withContext(Dispatchers.IO) {
            context.assets.open(problem.image).use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()
        }
    }

    @Composable
    fun Panel(onLeftSide: Boolean) {
        // Panel slides in from whichever screen edge it lives on
        val edge = if (onLeftSide) Alignment.End else Alignment.Start
        AnimatedVisibility(
            visible = panelOpen && panelOnLeft == onLeftSide,
            enter = expandHorizontally(expandFrom = edge),
            exit = shrinkHorizontally(shrinkTowards = edge),
        ) {
            InfoPanel(
                problem = problem,
                pageIndex = pageIndex,
                problems = problems,
                pagerState = pagerState,
                inverted = inverted,
                onToggleInvert = onToggleInvert,
            )
        }
    }

    Row(Modifier.fillMaxSize()) {
        Panel(onLeftSide = true)
        // Board: fills everything; tap a side to scoot it over and show the
        // info panel on that side (handedness-friendly)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures { offset -> onTap(offset.x < size.width / 2f) }
                }
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (problem.boxes != null) {
                AnimatedBoard(problem.boxes, inverted, Modifier.aspectRatio(516f / 330f))
            } else if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "Bongard problem board",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (inverted) invertFilter else null,
                )
            }
        }
        Panel(onLeftSide = false)
    }
}

@Composable
private fun InfoPanel(
    problem: Problem,
    pageIndex: Int,
    problems: List<Problem>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    inverted: Boolean,
    onToggleInvert: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showJump by remember { mutableStateOf(false) }
    var revealed by rememberSaveable(problem.num) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BP #${problem.num}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${pageIndex + 1} / ${problems.size}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { showJump = true },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onToggleInvert) {
                Icon(
                    if (inverted) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = "Toggle inverted colors",
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Designer: ${problem.designer}",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "from Harry Foundalis' collection at foundalis.com",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(18.dp))
        if (!revealed) {
            OutlinedButton(onClick = { revealed = true }) { Text("Show solution") }
        } else if (problem.left.isEmpty() && problem.right.isEmpty()) {
            Text(
                "No solution listed for this one — you're on your own, dawg.",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
            )
        } else {
            SolutionCell("Left side", problem.left)
            Spacer(Modifier.height(12.dp))
            SolutionCell("Right side", problem.right)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { revealed = false }) { Text("Hide solution") }
        }
    }

    if (showJump) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("Go to problem #") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = input.toIntOrNull()
                    val idx = problems.indexOfFirst { it.num == n }
                    if (idx >= 0) scope.launch { pagerState.scrollToPage(idx) }
                    showJump = false
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showJump = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SolutionCell(label: String, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AnimatedBoard(boxes: List<String>, inverted: Boolean, modifier: Modifier) {
    val loader = rememberGifLoader()
    val borderColor = MaterialTheme.colorScheme.onBackground
    val filter = if (inverted) invertFilter else null

    @Composable
    fun Page(page: List<String>, modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0..2) {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (c in 0..1) {
                        AsyncImage(
                            model = "file:///android_asset/${page[r * 2 + c]}",
                            imageLoader = loader,
                            contentDescription = null,
                            colorFilter = filter,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .border(1.dp, borderColor),
                        )
                    }
                }
            }
        }
    }

    Row(modifier) {
        Page(boxes.subList(0, 6), Modifier.weight(220f))
        Spacer(Modifier.weight(68f))
        Page(boxes.subList(6, 12), Modifier.weight(220f))
    }
}
