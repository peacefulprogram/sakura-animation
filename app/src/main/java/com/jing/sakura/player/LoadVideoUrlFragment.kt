package com.jing.sakura.player

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.jing.sakura.R
import com.jing.sakura.data.Resource
import com.jing.sakura.databinding.LoadVideoUrlFramentBinding
import com.jing.sakura.repo.WebPageRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoadVideoUrlFragment: Fragment() {

    @Inject
    lateinit var repository: WebPageRepository
    private lateinit var episodeUrl: String
    private lateinit var animeTitle: String
    private lateinit var viewBinding: LoadVideoUrlFramentBinding

    private lateinit var navController: NavController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navController = findNavController()
        LoadVideoUrlFragmentArgs.fromBundle(requireArguments()).let {
            episodeUrl = it.episodeUrl
            animeTitle = it.animeTitle
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewBinding =
            LoadVideoUrlFramentBinding.inflate(inflater, container, false)
        viewBinding.root.run {
            requestFocus()
            setOnKeyListener { _, keyCode, _ ->
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        findNavController().popBackStack()
                        true
                    }
                    else -> false
                }

            }
        }
        viewBinding.loadHint.text = "正在加载视频链接: $animeTitle"
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = repository.fetchVideoUrl(episodeUrl)) {
                is Resource.Error -> onLoadError(result.message)
                is Resource.Success -> onFoundVideoUrl(result.data)
                else -> {}
            }
        }
        return viewBinding.root
    }

    private fun onLoadError(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            delay(1000L)
            findNavController().popBackStack()
        }
    }

    private fun onFoundVideoUrl(url: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            navController.navigate(
                LoadVideoUrlFragmentDirections.actionLoadVideoUrlFragmentToAnimePlayerFragment(
                    url,
                    animeTitle
                ),
                navOptions {
                    popUpTo(R.id.animeDetailFragment) {
                        inclusive = false
                    }
                }
            )
        }

    }

}