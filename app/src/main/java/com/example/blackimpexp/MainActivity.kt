package com.example.blackimpexp

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.BlockedNumberContract.BlockedNumbers
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import java.io.BufferedInputStream
import java.io.BufferedOutputStream


const val FILE_NAME = "contacts_blacklist.json"

data class BlacklistContacts(val count: Int, var list: ArrayList<String>)

class MainActivity : AppCompatActivity() {
    private val fileType = "application/json"
    private val fileTypes = arrayOf("application/json", "application/octet-stream")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dialerRoleRequest = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
           // Log.d(TAG, "dialerRoleRequest succeeded: ${it.resultCode == Activity.RESULT_OK}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(ROLE_SERVICE) as RoleManager

            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            )
                dialerRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        } else {
            // Android 9 and below
            val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
            if (telecomManager.defaultDialerPackage != packageName) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                intent.putExtra(
                    TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                    packageName
                )
                startActivity(intent)
            }
        }

        setContentView(R.layout.activity_main)

        val btnExport: Button = findViewById(R.id.btn_export)
        val btnImport: Button = findViewById(R.id.btn_import)

        btnExport.setOnClickListener {
            createRegisterForResult.launch(FILE_NAME)
        }

        btnImport.setOnClickListener {
            //openRegisterForResult.launch(arrayOf(fileType))
            openRegisterForResult.launch(fileTypes)
        }
    }
    @SuppressLint("Range")
    private fun getBlocked(): ArrayList<String> {
        val list = arrayListOf<String>()
        val record = contentResolver.query(
            BlockedNumbers.CONTENT_URI,
            arrayOf(
                BlockedNumbers.COLUMN_ID, BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                BlockedNumbers.COLUMN_E164_NUMBER
            ), null, null, null
        )

        if (record != null && record.count != 0) {
            if (record.moveToFirst()) {
                do {
                    val blockNumber =
                        record.getString(record.getColumnIndex(BlockedNumbers.COLUMN_ORIGINAL_NUMBER))
                    list.add(blockNumber)
                } while (record.moveToNext())
            }
            record.close()
        }
        //}
        list.sort()
        return list
    }


    private fun Context.toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun writeFile(context: Context, outputJson: String, uri: Uri): Boolean {
        var bos: BufferedOutputStream? = null
        try {
            bos = BufferedOutputStream(context.contentResolver.openOutputStream(uri))
            bos.write(outputJson.toByteArray())
            bos.close()

        } catch (e: Exception) {
            // Notify User of fail
            context.toast(getString(R.string.text_removed_number))
            return false
        } finally {
            try {
                if (bos != null) {
                    bos.flush()
                    bos.close()
                }
            } catch (ignored: Exception) {
            }
        }
        return true
    }


    private fun putNumberOnBlocked(
        number: String
    ) {
        val values = ContentValues()
        values.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
        contentResolver.insert(
            BlockedNumbers.CONTENT_URI,
            values
        )
    }


private fun readFile(context: Context, uri: Uri): StringBuilder? {
    var bis: BufferedInputStream? = null
    val stringBuilder = StringBuilder()

    try {
        // Open the input stream for the file
        bis = BufferedInputStream(context.contentResolver.openInputStream(uri))
        val buffer = ByteArray(1024)
        var bytesRead: Int

        while (bis.read(buffer).also { bytesRead = it } != -1) {
            stringBuilder.append(String(buffer, 0, bytesRead))
        }

    } catch (e: Exception) {
        context.toast(getString(R.string.text_failed_to_read))
        return null
    } finally {
        try {
            // Close the input stream if it was opened
            bis?.close()
        } catch (ignored: Exception) {
        }
    }

    return stringBuilder
}


    private val openRegisterForResult =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val stringBuilder: StringBuilder? = readFile(this, uri)
                if (stringBuilder != null) {
                    // parse to object
                    val blacklistContacts: BlacklistContacts =
                        Gson().fromJson(stringBuilder.toString(), BlacklistContacts::class.java)

                    // load numbers to blacklist
                    blacklistContacts.list.forEach {
                        putNumberOnBlocked(it)
                    }
                    this.toast(getString(R.string.text_added_numbers))
                }
            } else {
                this.toast(getString(R.string.error_file))
            }
        }

    private val createRegisterForResult =
        registerForActivityResult(ActivityResultContracts.CreateDocument(fileType)) { uri ->
            if (uri != null) {
                // Get the data
                lateinit var outputJson: String

                val list = getBlocked()
                val data = BlacklistContacts(
                    list.size,
                    list
                )
                val gson = Gson()

                outputJson = gson.toJson(data)

                // Save the data into the selected file
                 writeFile(this, outputJson, uri)

                this.toast(getString(R.string.text_export_success))
            } else
                this.toast(getString(R.string.error_file))
        }
}