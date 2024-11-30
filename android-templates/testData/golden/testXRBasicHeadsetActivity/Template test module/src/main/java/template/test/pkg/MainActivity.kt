package template.test.pkg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import template.test.pkg.ui.theme.AppTheme
import com.google.vr.androidx.xr.compose.spatial.Edge
import com.google.vr.androidx.xr.compose.spatial.LocalSession
import com.google.vr.androidx.xr.compose.spatial.Orbiter
import com.google.vr.androidx.xr.compose.spatial.Subspace
import com.google.vr.androidx.xr.compose.spatial.XrMode
import com.google.vr.androidx.xr.compose.spatial.currentOrNull
import com.google.vr.androidx.xr.compose.spatial.innerEdge
import com.google.vr.androidx.xr.compose.ui.layout.SubspaceModifier
import com.google.vr.androidx.xr.compose.ui.layout.height
import com.google.vr.androidx.xr.compose.ui.layout.movable
import com.google.vr.androidx.xr.compose.ui.layout.resizable
import com.google.vr.androidx.xr.compose.ui.layout.width
import com.google.vr.androidx.xr.compose.ui.subspace.SpatialPanel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                val session = LocalSession.currentOrNull
                if (XrMode.isSpatializationEnabled) {
                    Subspace {
                        MySpatialContent(onRequestHomeSpaceMode = { session?.requestHomeSpaceMode() })
                    }
                } else {
                    My2DContent(onRequestFullSpaceMode = { session?.requestFullSpaceMode() })
                }
            }
        }
    }
}

@Composable
fun MySpatialContent(onRequestHomeSpaceMode: () -> Unit) {
    SpatialPanel(SubspaceModifier.width(1280.dp).height(800.dp).resizable().movable()) {
        Surface(shape = RoundedCornerShape(32.dp)) {
            MainContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp)
            )
        }
        Orbiter(
            position = Edge.Top,
            offset = innerEdge(offset = 20.dp),
            alignment = Alignment.End
        ) {
            HomeSpaceModeIconButton(
                onClick = onRequestHomeSpaceMode,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@Composable
fun My2DContent(onRequestFullSpaceMode: () -> Unit) {
    Surface {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MainContent(modifier = Modifier.padding(48.dp))
            if (XrMode.isEnabledAndSupported) {
                FullSpaceModeIconButton(
                    onClick = onRequestFullSpaceMode,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun MainContent(modifier: Modifier = Modifier) {
    Text(text = stringResource(R.string.hello_android_xr), modifier = modifier)
}

@Composable
fun FullSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_full_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_full_space_mode)
        )
    }
}

@Composable
fun HomeSpaceModeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_home_space_mode_switch),
            contentDescription = stringResource(R.string.switch_to_home_space_mode)
        )
    }
}

@PreviewLightDark
@Composable
fun My2dContentPreview() {
    AppTheme {
        My2DContent(onRequestFullSpaceMode = {})
    }
}

@Preview(showBackground = true)
@Composable
fun FullSpaceModeButtonPreview() {
    AppTheme {
        FullSpaceModeIconButton(onClick = {})
    }
}

@PreviewLightDark
@Composable
fun HomeSpaceModeButtonPreview() {
    AppTheme {
        HomeSpaceModeIconButton(onClick = {})
    }
}