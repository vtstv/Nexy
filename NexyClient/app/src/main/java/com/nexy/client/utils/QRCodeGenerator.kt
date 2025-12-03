/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QRCodeGenerator {
    
    fun generateQRCode(data: String, width: Int = 512, height: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, width, height)
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }
    
    fun generateUserQRCode(userId: String, username: String): Bitmap {
        val data = "nexy://user/$userId?username=$username"
        return generateQRCode(data)
    }
    
    fun generateInviteQRCode(inviteCode: String): Bitmap {
        val data = "nexy://invite/$inviteCode"
        return generateQRCode(data)
    }
}
