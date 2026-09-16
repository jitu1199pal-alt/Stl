package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AdMobBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        bottomBar = { AdMobBanner() },
        containerColor = Color(0xFF020617)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Privacy & Data Integrity Guarantee", fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "1. 100% Offline Local Processing:\n" +
                                "All CNC G-code (.tap, .nc, .txt, .bin), 3D STL meshes, ArtCAM relief files (.rlf), and AutoCAD drawings (.dxf) are parsed and rendered directly on your device using local GPU hardware acceleration. Your designs and proprietary machine files are never uploaded or transmitted to any external server.\n\n" +
                                "2. Minimal System Permissions:\n" +
                                "The app uses Android Storage Access Framework (SAF) only for opening files explicitly picked by you. No access to private contacts, microphone, camera, or device storage without permission.\n\n" +
                                "3. Advertising & Google AdMob:\n" +
                                "To keep this tool free, Google AdMob displays banner ads. AdMob uses standard advertising identifiers (AAID) in strict compliance with Google Play Store policies.\n\n" +
                                "4. Contact Developer:\n" +
                                "For any questions or privacy inquiries, contact: jitu1199pal@gmail.com",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
