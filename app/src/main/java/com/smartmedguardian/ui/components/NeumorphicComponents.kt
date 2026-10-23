package com.smartmedguardian.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartmedguardian.ui.theme.*

@Composable
fun NeumorphicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = Color.Unspecified,
    icon: (@Composable () -> Unit)? = null
) {
    val isDark = LocalThemeIsDark.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val elevation by animateDpAsState(targetValue = if (isPressed) 2.dp else 6.dp, label = "elevation")
    
    val buttonBg = if (isDark && (color == MaterialTheme.colorScheme.surface || color == SurfaceLight)) SurfaceDark else color

    val effectiveTextColor = if (textColor != Color.Unspecified) {
        textColor
    } else when {
        buttonBg == MaterialTheme.colorScheme.surface || buttonBg == MaterialTheme.colorScheme.background || buttonBg == SurfaceDark || buttonBg == SurfaceLight -> {
            MaterialTheme.colorScheme.onSurface
        }
        buttonBg == MaterialTheme.colorScheme.primary || buttonBg == MedicalGreen || buttonBg == MedicalGreenDark -> {
            if (isDark) Color(0xFF022C22) else Color.White
        }
        buttonBg == MaterialTheme.colorScheme.error || buttonBg == ErrorLight || buttonBg == ErrorDark -> {
            Color.White
        }
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .neumorphicShadow(
                elevation = elevation,
                isPressed = isPressed,
                cornerRadius = 20.dp
            )
            .clip(RoundedCornerShape(20.dp))
            .background(buttonBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides effectiveTextColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = effectiveTextColor
                    )
                )
            }
        }
    }
}

@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalThemeIsDark.current
    val cardBg = if (isDark && (color == MaterialTheme.colorScheme.surface || color == SurfaceLight)) SurfaceDark else color
    val contentColor = if (isDark) TextDarkOnDark else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .neumorphicShadow(
                elevation = if (isDark) 5.dp else 8.dp,
                cornerRadius = cornerRadius
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(cardBg)
            .padding(16.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(content = content)
        }
    }
}

@Composable
fun NeumorphicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .neumorphicShadow(
                elevation = 4.dp,
                isPressed = true,
                cornerRadius = 20.dp
            )
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextLight) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeumorphicTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicShadow(elevation = 4.dp, cornerRadius = 0.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
            navigationIcon = {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        // Back icon placeholder
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
    }
}

@Composable
fun NeumorphicIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface
) {
    val isDark = LocalThemeIsDark.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val elevation by animateDpAsState(targetValue = if (isPressed) 2.dp else 6.dp, label = "elevation")
    val buttonBg = if (isDark && (color == MaterialTheme.colorScheme.surface || color == SurfaceLight)) SurfaceDark else color
    val iconTint = if (isDark) TextDarkOnDark else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .neumorphicShadow(
                elevation = elevation,
                isPressed = isPressed,
                cornerRadius = 16.dp
            )
            .clip(RoundedCornerShape(16.dp))
            .background(buttonBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides iconTint) {
            icon()
        }
    }
}

@Composable
fun NeumorphicFAB(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val elevation by animateDpAsState(targetValue = if (isPressed) 4.dp else 12.dp, label = "elevation")
    
    Box(
        modifier = modifier
            .neumorphicShadow(
                elevation = elevation,
                isPressed = isPressed,
                cornerRadius = 24.dp,
                darkShadowColor = if (containerColor == MaterialTheme.colorScheme.surface || containerColor == MaterialTheme.colorScheme.background) Color.Unspecified else containerColor.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
        }
    }
}

@Composable
fun Modifier.neumorphicShadow(
    elevation: Dp = 8.dp,
    isPressed: Boolean = false,
    cornerRadius: Dp = 20.dp,
    lightShadowColor: Color = Color.Unspecified,
    darkShadowColor: Color = Color.Unspecified
): Modifier {
    val isDark = LocalThemeIsDark.current
    val shadowColor = if (darkShadowColor != Color.Unspecified) {
        darkShadowColor
    } else {
        if (isDark) DarkShadowDark else DarkShadowLight
    }
    
    val lightColor = if (lightShadowColor != Color.Unspecified) {
        lightShadowColor
    } else {
        if (isDark) LightShadowDark else LightShadowLight
    }

    return this.drawBehind {
        val shadowColorArgb = shadowColor.toArgb()
        val lightColorArgb = lightColor.toArgb()
        
        drawIntoCanvas { canvas ->
            val paint = Paint()
            val frameworkPaint = paint.asFrameworkPaint()
            
            if (!isPressed) {
                // In Dark mode, use controlled shadow radius and subtle offsets for a clean 3D raised surface
                val shadowRadius = if (isDark) (elevation.toPx() * 0.75f).coerceAtLeast(1f) else elevation.toPx()
                val offset = if (isDark) (elevation.toPx() * 0.4f).coerceAtLeast(1f) else elevation.toPx()

                // Dark Shadow (Bottom Right)
                frameworkPaint.color = shadowColorArgb
                frameworkPaint.setShadowLayer(
                    shadowRadius,
                    offset,
                    offset,
                    shadowColorArgb
                )
                canvas.drawRoundRect(
                    0f, 0f, size.width, size.height,
                    cornerRadius.toPx(), cornerRadius.toPx(), paint
                )
                
                // Light Highlight (Top Left)
                frameworkPaint.color = lightColorArgb
                frameworkPaint.setShadowLayer(
                    shadowRadius,
                    -offset * 0.6f,
                    -offset * 0.6f,
                    lightColorArgb
                )
                canvas.drawRoundRect(
                    0f, 0f, size.width, size.height,
                    cornerRadius.toPx(), cornerRadius.toPx(), paint
                )
            }
        }
    }
}
