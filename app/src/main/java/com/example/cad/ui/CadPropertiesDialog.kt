package com.example.cad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.parser.DxfModel

@Composable
fun CadPropertiesDialog(
    model: DxfModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Drawing Properties",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        PropertyRow("File Name", model.fileName)
                        PropertyRow("Format", if (model.fileName.endsWith(".dwg", ignoreCase = true)) "AutoCAD DWG" else "AutoCAD DXF")
                        PropertyRow("File Size", formatFileSize(model.fileSize))
                        PropertyRow("Version", model.dwgVersion ?: "AutoCAD 2000 (AC1015)")
                        PropertyRow("Units", "Millimeters (mm)")
                        PropertyRow("Layer Count", "${model.layers.size}")
                        PropertyRow("Entity Count", "${model.entities.size}")
                        PropertyRow("Block Count", "${model.blockCount}")
                        PropertyRow("XREF Count", "0 (Self-contained)")
                        PropertyRow(
                            "Model Bounds",
                            "X: [%.1f, %.1f]\nY: [%.1f, %.1f]".format(
                                model.bounds.minX, model.bounds.maxX,
                                model.bounds.minY, model.bounds.maxY
                            )
                        )
                        PropertyRow(
                            "Dimensions",
                            "%.1f × %.1f mm".format(model.bounds.sizeX, model.bounds.sizeY)
                        )
                    }

                    if (model.entityStats.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Entity Breakdown",
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        items(model.entityStats.entries.toList().filter { it.value > 0 }.size) { idx ->
                            val entry = model.entityStats.entries.toList().filter { it.value > 0 }[idx]
                            PropertyRow(entry.key, "${entry.value}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", color = Color(0xFF020617), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 13.sp)
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) "%.2f MB".format(mb) else "%.1f KB".format(kb)
}
