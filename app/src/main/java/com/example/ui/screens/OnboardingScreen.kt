package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GestoShareBrandMark
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.GlassStatusBadge
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GestoTheme
import com.example.ui.theme.glassSurface
import kotlinx.coroutines.launch

private data class OnboardingPageData(
    val badge: String,
    val title: String,
    val description: String,
    val highlights: List<String>,
    val icon: ImageVector? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = GestoTheme.colors
    val scope = rememberCoroutineScope()

    val pages = listOf(
        OnboardingPageData(
            badge = "WELCOME",
            title = "Share Without Touching",
            description = "GestoShare transfers photos, videos, PDFs, notes, and links offline between Android phones using hand gestures.",
            highlights = listOf(
                "100% offline peer-to-peer radio links (no internet required)",
                "Custom HaGRIDv2 + MediaPipe AI runs locally on-device",
                "Zero tracking, zero cloud servers, instant setup"
            )
        ),
        OnboardingPageData(
            badge = "5 GESTURES",
            title = "Master the Air Workflow",
            description = "Hold each steady gesture briefly until the temporal stabilizer confirms it.",
            highlights = listOf(
                "👍 LIKE — Activate gesture session",
                "✊ FIST — Grab staged files / content",
                "👉 POINT — Cycle connected nearby recipients",
                "🔫 THREE_GUN — Release & send offer",
                "✋ STOP — Cancel active workflow anytime"
            ),
            icon = Icons.Default.PanTool
        ),
        OnboardingPageData(
            badge = "P2P CLUSTER",
            title = "Multi-Phone Discovery & Auth",
            description = "Discover Redmi, Infinix, and Pixel peers simultaneously using Google Nearby Connections Cluster topology.",
            highlights = listOf(
                "Connect to multiple nearby phones at once",
                "Verify matching 6-digit security codes on both screens",
                "Switch targets inside Camera Preview with POINT or tap"
            ),
            icon = Icons.Default.Devices
        ),
        OnboardingPageData(
            badge = "VERIFIED I/O",
            title = "SHA-256 & Scoped Storage Safe",
            description = "Every incoming payload is verified cryptographically before saving to your public Downloads/GestoShare folder.",
            highlights = listOf(
                "Reads Nearby payloads via Android ContentResolver URIs",
                "Validates exact byte count + SHA-256 checksum",
                "Exports cleanly via MediaStore Downloads after verification"
            ),
            icon = Icons.Default.Lock
        ),
        OnboardingPageData(
            badge = "SAFETY FIRST",
            title = "Permissions & Dry Run Lock",
            description = "Practice gestures safely in Debug / Dry Run Mode, or arm Gesture Transfer Mode when ready to send.",
            highlights = listOf(
                "Camera permission enables local MediaPipe hand tracking",
                "Nearby / Bluetooth permissions enable offline peer discovery",
                "Built-in Diagnostics Center captures full error reports"
            ),
            icon = Icons.Default.Science
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("onboarding_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlassStatusBadge(
                label = "STEP ${pagerState.currentPage + 1} / ${pages.size}",
                color = palette.accentPrimary
            )

            TextButton(
                onClick = onComplete,
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Text(
                    text = "Skip",
                    color = palette.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pageIdx ->
            val page = pages[pageIdx]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 26.dp,
                    fillColor = palette.glassFillElevated,
                    borderAccent = palette.accentPrimary.copy(alpha = 0.45f),
                    borderWidth = 1.5.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (page.icon == null) {
                            GestoShareBrandMark(size = 76.dp, cornerRadius = 20.dp)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .glassSurface(
                                        cornerRadius = 22.dp,
                                        fillColor = palette.glassFillInset,
                                        borderAccent = palette.accentPrimary.copy(alpha = 0.7f),
                                        borderWidth = 1.5.dp
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = page.icon,
                                    contentDescription = page.title,
                                    tint = palette.accentPrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        GlassStatusBadge(label = page.badge, color = palette.accentSecondary)

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = page.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSurface(
                                    cornerRadius = 16.dp,
                                    fillColor = palette.glassFillInset,
                                    borderAccent = palette.glassBorder
                                )
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            page.highlights.forEach { bullet ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "•",
                                        color = palette.accentPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = bullet,
                                        fontSize = 12.sp,
                                        color = palette.textPrimary,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }

                        if (pageIdx == pages.lastIndex) {
                            Spacer(modifier = Modifier.height(14.dp))
                            GlassOutlinedButton(
                                onClick = onRequestPermissions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_grant_permissions_button"),
                                accentColor = palette.success
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Grant Camera & Nearby Permissions",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(7.dp)
                            .width(if (active) 24.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) palette.accentPrimary else palette.textMuted.copy(alpha = 0.4f)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pagerState.currentPage > 0) {
                    GlassOutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("onboarding_back_button"),
                        accentColor = palette.textSecondary,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back", fontWeight = FontWeight.SemiBold)
                    }
                }

                GlassButton(
                    onClick = {
                        if (isLastPage) {
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(if (pagerState.currentPage > 0) 1.4f else 1f)
                        .height(48.dp)
                        .testTag("onboarding_next_button"),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (!isLastPage) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
