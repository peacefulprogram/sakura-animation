package com.jing.sakura.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.jing.sakura.R

class SplashFragment : Fragment() {

    private val viewModel: SplashViewModel by viewModels()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.shouldGoHome.observe(viewLifecycleOwner) {
            if (it) {
                findNavController().navigate(SplashFragmentDirections.actionSplashFragmentToHomeFragment(),
                    navOptions {
                        popUpTo(R.id.splashFragment) {
                            inclusive = true
                        }
                    })
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.splash_fragment, container, false)
    }
}