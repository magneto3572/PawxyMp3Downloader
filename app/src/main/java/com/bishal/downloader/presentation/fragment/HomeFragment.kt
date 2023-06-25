package com.bishal.downloader.presentation.fragment

import RealPathUtil
import android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bishal.downloader.R
import com.bishal.downloader.databinding.FragmentHomeBinding
import com.bishal.downloader.domain.utils.Utils
import com.bishal.downloader.domain.utils.superNavigate
import com.bishal.downloader.presentation.basefragment.BaseFragment
import com.bishal.downloader.presentation.viewmodel.HomeViewModel
import com.bishal.ytdlplibrary.YoutubeDL.getInstance
import com.bishal.ytdlplibrary.mapper.VideoInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding, HomeViewModel>() {

    private var info : MutableStateFlow<VideoInfo?> = MutableStateFlow(null)
    private var url : String = String()
    private var outputPath : String? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListener()
        setupObserver()
    }



    private fun setupListener(){
        binding.apply {
            imageView3.setOnClickListener {
                editText.setText("")
            }

            folderSelectionView.setOnClickListener {
                openFileExplorer()
            }

            downloadBtn.setOnClickListener {
                if(editText.text.isNotBlank()){
                    folderSelectionView.isEnabled = false
                    folderSelectionView.isClickable = false
                    editText.isClickable =  false
                    editText.isEnabled =  false
                    editText.isClickable = false
                    downloadBtn.text = "Grabbing Info.."
                    downloadBtn.setIconResource(R.drawable.icons)
                    getStreamInfo(editText.text.trim().toString())
                }else{
                    folderSelectionView.isEnabled = true
                    folderSelectionView.isClickable = true
                    editText.isClickable =  true
                    editText.isEnabled =  true
                    Toast.makeText(
                        requireContext(),
                        "Url cannot be empty",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            //get directory path
            grantUriPermission(result?.data?.data!!)
            outputPath = RealPathUtil.getRealPath(requireContext(), Utils.getDirectoryShortPath(result))
            binding.apply {
                folderSelectionView.text = outputPath.toString()
            }
        } else {
            Toast.makeText(requireContext(), "No directory selected", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun grantUriPermission(uri: Uri) {
        requireContext().applicationContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    private fun openFileExplorer() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        launcher.launch(intent)
    }

    private fun setupObserver(){
        lifecycleScope.launch{
            info.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.CREATED).collectLatest { info ->
                if (info != null){
                    val action =  HomeFragmentDirections.actionHomeFragmentToProgressFragment(info.fulltitle.toString(), info.likeCount.toString(), info.viewCount.toString(), info.thumbnail.toString(), url, info.fileSize, outputPath.toString(), info.title?.trim().toString(), info.id.toString())
                    superNavigate(action)
                }
            }
        }
    }

    private fun getStreamInfo(streamUrl: String) {
        runCatching {
            url = streamUrl
            lifecycleScope.launch(Dispatchers.IO) {
               val streamInfo =  async(Dispatchers.IO) {
                   getInstance().getInfo(url)
                }
                info.value = streamInfo.await()
            }
        }.getOrElse {
            it.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun isExternalStorageManager(
        packageName: String,
        context: Context,
        packageManger: PackageManager
    ): Boolean {
        return hasPermission(packageName, MANAGE_EXTERNAL_STORAGE, context, packageManger)
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    fun hasPermission(
        packageName: String,
        permission: String,
        context: Context,
        packageManger: PackageManager
    ): Boolean {
        val granted: Boolean
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        val info = packageManger.getApplicationInfo(packageName, 0)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.permissionToOp(permission)!!,
            info.uid,
            packageName
        )
        granted = if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } else {
            (mode == AppOpsManager.MODE_ALLOWED)
        }

        return granted
    }
}