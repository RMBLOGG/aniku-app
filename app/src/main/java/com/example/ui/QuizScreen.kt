package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.AnimeRaw
import kotlinx.coroutines.delay
import kotlin.random.Random

// ============================================================================
//  PALET WARNA — "Trivia Spotlight": panggung kuis temaram + sorot lampu ambar.
//  Beda dari Neon Arena (Gacha) & merah Chat Room, biar quiz kerasa jadi
//  "acara" tersendiri. Ring countdown di sekitar poster jadi elemen signature -
//  bukan sekadar hiasan, itu representasi visual dari bonus jawaban cepat.
// ============================================================================
private object Spotlight {
    val Void = Color(0xFF0B0A12)
    val Stage = Color(0xFF16131F)
    val Card = Color(0xFF1E1A2B)
    val Amber = Color(0xFFFFB020)
    val AmberDim = Color(0xFF7A5116)
    val Correct = Color(0xFF3DDC84)
    val Wrong = Color(0xFFFF5470)
    val TextDim = Color(0xFFB8B2C6)
}

private const val QUESTION_SECONDS = 10
private const val FAST_THRESHOLD_SECONDS = 5

private sealed class QuizUiState {
    object Idle : QuizUiState()
    object Loading : QuizUiState()
    data class Blocked(val message: String, val needsClan: Boolean, val needsDiamond: Boolean) : QuizUiState()
    data class Question(
        val poster: String,
        val choices: List<String>,
        val correctAnswer: String,
        val freeRemaining: Int,
        val isFreeRound: Boolean
    ) : QuizUiState()
    data class Answered(
        val poster: String,
        val choices: List<String>,
        val correctAnswer: String,
        val picked: String,
        val isCorrect: Boolean,
        val selfXp: Int,
        val mateXp: Int,
        val perfectBonus: Boolean
    ) : QuizUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: AnikuViewModel,
    onBack: () -> Unit,
    onJoinClanClick: () -> Unit,
    onTopUpClick: () -> Unit
) {
    var uiState by remember { mutableStateOf<QuizUiState>(QuizUiState.Idle) }
    var secondsLeft by remember { mutableStateOf(QUESTION_SECONDS) }
    var answerStartMillis by remember { mutableStateOf(0L) }

    fun startRound() {
        uiState = QuizUiState.Loading
        viewModel.playQuizRound(cost = 5000) { playResult, playError ->
            if (playResult == null) {
                val msg = playError ?: "Gagal mulai quiz"
                uiState = QuizUiState.Blocked(
                    message = msg,
                    needsClan = msg.contains("clan", ignoreCase = true),
                    needsDiamond = msg.contains("DM", ignoreCase = true) || msg.contains("Diamond", ignoreCase = true)
                )
                return@playQuizRound
            }
            viewModel.fetchQuizQuestion(decoyCount = 3) { correct, decoys, fetchError ->
                if (correct == null) {
                    uiState = QuizUiState.Blocked(
                        message = fetchError ?: "Gagal ambil soal, coba lagi",
                        needsClan = false,
                        needsDiamond = false
                    )
                    return@fetchQuizQuestion
                }
                val correctTitle = correct.title
                val poster = correct.poster
                val allChoices = (decoys + correctTitle).shuffled(Random(System.nanoTime()))
                answerStartMillis = System.currentTimeMillis()
                secondsLeft = QUESTION_SECONDS
                uiState = QuizUiState.Question(
                    poster = poster,
                    choices = allChoices,
                    correctAnswer = correctTitle,
                    freeRemaining = playResult.free_remaining,
                    isFreeRound = playResult.is_free
                )
            }
        }
    }

    fun submitAnswer(picked: String, q: QuizUiState.Question) {
        val elapsedSeconds = (System.currentTimeMillis() - answerStartMillis) / 1000.0
        val isCorrect = picked == q.correctAnswer
        val isFast = isCorrect && elapsedSeconds <= FAST_THRESHOLD_SECONDS
        viewModel.submitQuizAnswer(isCorrect, isFast) { result, _ ->
            uiState = QuizUiState.Answered(
                poster = q.poster,
                choices = q.choices,
                correctAnswer = q.correctAnswer,
                picked = picked,
                isCorrect = isCorrect,
                selfXp = result?.self_xp_awarded ?: 0,
                mateXp = result?.mate_xp_awarded ?: 0,
                perfectBonus = result?.perfect_bonus_awarded ?: false
            )
        }
    }

    // Countdown - auto-submit sebagai salah kalau waktu habis
    LaunchedEffect(uiState) {
        val q = uiState as? QuizUiState.Question ?: return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
        if (uiState is QuizUiState.Question) {
            submitAnswer("__timeout__", q)
        }
    }

    Scaffold(
        containerColor = Spotlight.Void,
        topBar = {
            TopAppBar(
                title = { Text("Tebak Anime", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Spotlight.Void)
            )
        }
    ) { padding ->
        val infiniteBg = rememberInfiniteTransition(label = "spotlight_breathe")
        val breathe by infiniteBg.animateFloat(
            initialValue = 780f, targetValue = 950f,
            animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Spotlight.Stage, Spotlight.Void),
                        radius = breathe
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "quizState"
            ) { state ->
                when (state) {
                    is QuizUiState.Idle -> IdleState(onStart = { startRound() })
                    is QuizUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Spotlight.Amber)
                    }
                    is QuizUiState.Blocked -> BlockedState(
                        state = state,
                        onJoinClanClick = onJoinClanClick,
                        onTopUpClick = onTopUpClick,
                        onRetry = { startRound() }
                    )
                    is QuizUiState.Question -> QuestionState(
                        state = state,
                        secondsLeft = secondsLeft,
                        onPick = { picked -> submitAnswer(picked, state) }
                    )
                    is QuizUiState.Answered -> AnsweredState(
                        state = state,
                        onNext = { startRound() }
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleState(onStart: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "idle_pulse")
    val glowScale by infinite.animateFloat(
        initialValue = 0.92f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale"
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )
    val shimmer by infinite.animateFloat(
        initialValue = -0.4f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                    .background(
                        Brush.radialGradient(listOf(Spotlight.Amber.copy(alpha = glowAlpha), Color.Transparent)),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Spotlight.Card)
                    .border(1.5.dp, Spotlight.Amber.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Theaters,
                    contentDescription = null,
                    tint = Spotlight.Amber,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "TEBAK ANIME DARI POSTER",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 19.sp,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "10 soal gratis tiap hari. Jawab bener nambah XP buat kamu\ndan semua member clan-mu.",
            color = Spotlight.TextDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Spotlight.Amber)
                .drawWithContent {
                    drawContent()
                    val x = shimmer * size.width
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                            start = Offset(x - 60f, 0f),
                            end = Offset(x + 60f, size.height)
                        )
                    )
                }
                .clickable { onStart() }
                .padding(horizontal = 36.dp, vertical = 16.dp)
        ) {
            Text("Mulai Main", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
private fun BlockedState(
    state: QuizUiState.Blocked,
    onJoinClanClick: () -> Unit,
    onTopUpClick: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(Brush.radialGradient(listOf(Spotlight.Amber.copy(alpha = 0.25f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state.needsClan) Icons.Default.Shield else Icons.Default.Diamond,
                contentDescription = null,
                tint = Spotlight.Amber,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            state.message,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        when {
            state.needsClan -> Button(
                onClick = onJoinClanClick,
                colors = ButtonDefaults.buttonColors(containerColor = Spotlight.Amber, contentColor = Color.Black),
                shape = RoundedCornerShape(50)
            ) { Text("Cari / Buat Clan", fontWeight = FontWeight.Bold) }

            state.needsDiamond -> Button(
                onClick = onTopUpClick,
                colors = ButtonDefaults.buttonColors(containerColor = Spotlight.Amber, contentColor = Color.Black),
                shape = RoundedCornerShape(50)
            ) { Text("Top-up Diamond", fontWeight = FontWeight.Bold) }

            else -> Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Spotlight.Amber, contentColor = Color.Black),
                shape = RoundedCornerShape(50)
            ) { Text("Coba Lagi", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun QuestionState(
    state: QuizUiState.Question,
    secondsLeft: Int,
    onPick: (String) -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "question_pulse")
    val ringPulse by infinite.animateFloat(
        initialValue = 0.97f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringPulse"
    )
    var visible by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) { visible = true }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = Spotlight.TextDim, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (state.isFreeRound) "Gratis · sisa ${state.freeRemaining} hari ini" else "Pakai Diamond",
                    color = Spotlight.TextDim,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Signature element: ring countdown berdenyut ngelilingin poster, warnanya
        // bergeser ke merah pas waktu mepet - representasi visual bonus "jawab cepat".
        val isUrgent = secondsLeft <= FAST_THRESHOLD_SECONDS
        val ringColor by animateColorAsState(
            targetValue = if (isUrgent) Spotlight.Wrong else Spotlight.Amber,
            animationSpec = tween(400),
            label = "ringColor"
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer {
                val s = if (isUrgent) ringPulse else 1f
                scaleX = s; scaleY = s
            }
        ) {
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .blur(24.dp)
                    .background(Brush.radialGradient(listOf(ringColor.copy(alpha = 0.35f), Color.Transparent)), CircleShape)
            )
            val progress = secondsLeft / QUESTION_SECONDS.toFloat()
            Canvas(modifier = Modifier.size(240.dp)) {
                drawArc(
                    color = ringColor.copy(alpha = 0.25f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            AsyncImage(
                model = state.poster,
                contentDescription = "Poster anime",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Spotlight.Void)
                    .border(1.dp, ringColor.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$secondsLeft", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Ini anime apa?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(16.dp))

        val letters = listOf("A", "B", "C", "D")
        state.choices.forEachIndexed { index, choice ->
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(280, delayMillis = index * 70)) +
                    slideInVertically(tween(280, delayMillis = index * 70)) { it / 3 }
            ) {
                var pressed by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "cardScale")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Spotlight.Card)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .clickable {
                            pressed = true
                            onPick(choice)
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Spotlight.Amber.copy(alpha = 0.16f))
                            .border(1.dp, Spotlight.Amber.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(letters[index], color = Spotlight.Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(choice, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun AnsweredState(
    state: QuizUiState.Answered,
    onNext: () -> Unit
) {
    val resultColor = if (state.isCorrect) Spotlight.Correct else Spotlight.Wrong
    val flashAlpha = remember { Animatable(0.35f) }
    LaunchedEffect(state) {
        flashAlpha.snapTo(0.35f)
        flashAlpha.animateTo(0f, animationSpec = tween(600))
    }
    val trophyPulse by rememberInfiniteTransition(label = "trophy_pulse").animateFloat(
        initialValue = 0.9f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "trophyPulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .background(Brush.radialGradient(listOf(resultColor.copy(alpha = flashAlpha.value), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = state.poster,
                    contentDescription = "Poster anime",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, resultColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.isCorrect) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = resultColor
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.isCorrect) "Benar!" else "Kurang tepat",
                    color = resultColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Jawaban benar: ${state.correctAnswer}",
                color = Spotlight.TextDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            if (state.isCorrect) {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Spotlight.Card)
                        .border(1.dp, Spotlight.Amber.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Spotlight.Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+${state.selfXp} XP buat kamu", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    if (state.mateXp > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "+${state.mateXp} XP dibagi ke semua member clan-mu",
                            color = Spotlight.TextDim,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    AnimatedVisibility(visible = state.perfectBonus) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Spotlight.Amber,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer { scaleX = trophyPulse; scaleY = trophyPulse }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Perfect hari ini! Bonus XP tambahan cair",
                                    color = Spotlight.Amber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = Spotlight.Amber, contentColor = Color.Black),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(50.dp)
            ) {
                Text("Soal Berikutnya", fontWeight = FontWeight.Bold)
            }
    }
}
