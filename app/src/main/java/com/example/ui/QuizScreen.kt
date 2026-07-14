package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.network.AnikuViewModel
import com.example.network.JikanAnimeData
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
                val poster = correct.images?.jpg?.large_image_url
                    ?: correct.images?.jpg?.image_url
                    ?: ""
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Spotlight.Stage, Spotlight.Void),
                        radius = 900f
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Spotlight.AmberDim.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Text("🎬", fontSize = 40.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Tebak Anime dari Poster",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
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
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Spotlight.Amber, contentColor = Color.Black),
            shape = RoundedCornerShape(50),
            modifier = Modifier.height(52.dp)
        ) {
            Text("Mulai Main", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
        Icon(
            imageVector = if (state.needsClan) Icons.Default.Shield else Icons.Default.Diamond,
            contentDescription = null,
            tint = Spotlight.Amber,
            modifier = Modifier.size(56.dp)
        )
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

        // Signature element: ring countdown ngelilingin poster - representasi
        // visual bonus "jawab cepat" (<5 detik dapet XP lebih banyak).
        Box(contentAlignment = Alignment.Center) {
            val progress = secondsLeft / QUESTION_SECONDS.toFloat()
            val ringColor = if (secondsLeft > FAST_THRESHOLD_SECONDS) Spotlight.Amber else Spotlight.Wrong
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
                    .background(Spotlight.Void),
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

        state.choices.forEach { choice ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Spotlight.Card)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .clickable { onPick(choice) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(choice, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AnsweredState(
    state: QuizUiState.Answered,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        AsyncImage(
            model = state.poster,
            contentDescription = "Poster anime",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state.isCorrect) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (state.isCorrect) Spotlight.Correct else Spotlight.Wrong
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (state.isCorrect) "Benar!" else "Kurang tepat",
                color = if (state.isCorrect) Spotlight.Correct else Spotlight.Wrong,
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
                        Text(
                            "🏆 Perfect hari ini! Bonus XP tambahan cair",
                            color = Spotlight.Amber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
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
