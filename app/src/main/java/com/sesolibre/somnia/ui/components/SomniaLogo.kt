package com.sesolibre.somnia.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sesolibre.somnia.R

/** Morado del logotipo (el de la imagen original de marca). */
private val LogoPurple = Color(0xFF9765DA)

@OptIn(ExperimentalTextApi::class)
private val Nunito = FontFamily(
    Font(
        R.font.nunito,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * Logotipo de Somnia: el ícono en un círculo (recortado de la imagen de marca,
 * con borde suavizado) y "somnia" en Nunito. Se dibuja en la app en vez de
 * usar un PNG pequeño, así se ve nítido en cualquier densidad de pantalla.
 * La fuente Nunito es OFL (licencia en assets/licenses/nunito-OFL.txt).
 */
@Composable
fun SomniaLogo(
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(height * 0.22f),
    ) {
        Image(
            painter = painterResource(R.drawable.somnia_mark),
            contentDescription = null,
            modifier = Modifier
                .size(height)
                .clip(CircleShape),
        )
        Text(
            text = "somnia",
            style = TextStyle(
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = (height.value * 0.62f).sp,
                color = LogoPurple,
            ),
        )
    }
}
