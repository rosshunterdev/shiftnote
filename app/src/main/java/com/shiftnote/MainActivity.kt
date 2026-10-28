package com.shiftnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.shiftnote.ui.navigation.ShiftNoteNavGraph
import com.shiftnote.ui.theme.ShiftNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShiftNoteTheme {
                val navController = rememberNavController()
                val container = (application as ShiftNoteApp).container
                ShiftNoteNavGraph(
                    navController = navController,
                    container = container
                )
            }
        }
    }
}
