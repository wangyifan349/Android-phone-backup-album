package com.example.photobackup

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.photobackup.api.ApiService
import com.example.photobackup.api.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_PICK_IMAGES = 1
    private val TAG = "PhotoBackup"
    private var userId: Int? = null

    private lateinit var usernameEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var registerButton: MaterialButton
    private lateinit var loginButton: MaterialButton
    private lateinit var uploadButton: MaterialButton
    private lateinit var listFilesButton: MaterialButton
    private lateinit var filesListView: ListView

    private val REQUEST_CODE_PERMISSIONS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        registerButton = findViewById(R.id.registerButton)
        loginButton = findViewById(R.id.loginButton)
        uploadButton = findViewById(R.id.uploadButton)
        listFilesButton = findViewById(R.id.listFilesButton)
        filesListView = findViewById(R.id.filesListView)

        // 检查并请求权限
        if (!hasStoragePermission()) {
            requestStoragePermission()
        }

        registerButton.setOnClickListener { registerUser() }
        loginButton.setOnClickListener { loginUser() }
        uploadButton.setOnClickListener { pickImage() }
        listFilesButton.setOnClickListener { listFiles() }
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "权限被拒绝，无法访问相册", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registerUser() {
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()

        val apiService = RetrofitClient.getRetrofitInstance().create(ApiService::class.java)
        apiService.registerUser(username, password).enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "注册成功，请登录", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "注册失败: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                Log.e(TAG, "注册失败: ${t.message}")
            }
        })
    }

    private fun loginUser() {
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()

        val apiService = RetrofitClient.getRetrofitInstance().create(ApiService::class.java)
        apiService.loginUser(username, password).enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                if (response.isSuccessful) {
                    userId = (response.body()?.get("user_id") as Double).toInt()
                    Toast.makeText(this@MainActivity, "登录成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "登录失败: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                Log.e(TAG, "登录失败: ${t.message}")
            }
        })
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGES)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_IMAGES && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val filePath = getRealPathFromURI(uri)
                filePath?.let { uploadFile(it) }
            }
        }
    }

    private fun getRealPathFromURI(contentUri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = contentResolver.query(contentUri, projection, null, null, null)
        cursor?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            it.moveToFirst()
            return it.getString(columnIndex)
        }
        return null
    }

    private fun uploadFile(filePath: String) {
        val file = File(filePath)
        val requestFile = RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

        val apiService = RetrofitClient.getRetrofitInstance().create(ApiService::class.java)
        apiService.uploadFile(RequestBody.create("text/plain".toMediaTypeOrNull(), userId.toString()), body).enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "文件上传成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "文件上传失败: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                Log.e(TAG, "上传失败: ${t.message}")
            }
        })
    }

    private fun listFiles() {
        userId?.let {
            val apiService = RetrofitClient.getRetrofitInstance().create(ApiService::class.java)
            apiService.listFiles(it).enqueue(object : Callback<List<String>> {
                override fun onResponse(call: Call<List<String>>, response: Response<List<String>>) {
                    if (response.isSuccessful) {
                        val files = response.body() ?: emptyList()
                        // 更新 ListView
                        // 这里可以使用 ArrayAdapter 或 RecyclerView 来显示文件列表
                        Toast.makeText(this@MainActivity, "文件列表: $files", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "获取文件列表失败: ${response.errorBody()?.string()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<String>>, t: Throwable) {
                    Log.e(TAG, "获取文件列表失败: ${t.message}")
                }
            })
        } ?: Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
    }
}
