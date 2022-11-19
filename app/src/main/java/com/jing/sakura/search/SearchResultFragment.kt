package com.jing.sakura.search

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jing.sakura.databinding.SearchResultFragmentBinding
import com.jing.sakura.extend.dpToPixels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchResultFragment : Fragment() {

    private val viewModel by viewModels<SearchResultViewModel>()

    private lateinit var viewBinding: SearchResultFragmentBinding

    private lateinit var keyword: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        keyword = SearchResultFragmentArgs.fromBundle(requireArguments()).keyword
        viewBinding = SearchResultFragmentBinding.inflate(inflater, container, false)

        val layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        viewBinding.container.addItemDecoration(
            SpaceDecorator(
                20.dpToPixels(requireContext()).toInt()
            )
        )
        val dataAdapter =
            AnimePagingDataAdapter({
                layoutManager.scrollToPositionWithOffset(
                    it,
                    0.dpToPixels(requireContext()).toInt()
                )
            }) {
                findNavController().navigate(
                    SearchResultFragmentDirections.actionSearchResultFragmentToAnimeDetailFragment(
                        it.url
                    )
                )
            }
        viewBinding.container.layoutManager = layoutManager
        viewBinding.container.adapter = dataAdapter
        dataAdapter.addLoadStateListener {
            when (it.refresh) {
                is LoadState.Loading -> {
                    viewBinding.progressCircular.visibility = View.VISIBLE
                    viewBinding.container.visibility = View.INVISIBLE
                }
                is LoadState.Error -> {
                    viewBinding.progressCircular.visibility = View.INVISIBLE
                    viewBinding.container.visibility = View.VISIBLE
                    Toast.makeText(
                        requireContext(),
                        "请求数据出错" + (it.refresh as LoadState.Error).error.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is LoadState.NotLoading -> {
                    viewBinding.progressCircular.visibility = View.INVISIBLE
                    viewBinding.container.visibility = View.VISIBLE
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getPagingData(keyword, this@SearchResultFragment::changeTotalCount)
                    .collect {
                        dataAdapter.submitData(it)
                    }
                dataAdapter.loadStateFlow.collect {

                }
            }
        }
        return viewBinding.root
    }

    fun changeTotalCount(total: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            viewBinding.totalCount.text = "共查到:$total"
        }
    }

    inner class SpaceDecorator(private val verticalSpacing: Int) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            super.getItemOffsets(outRect, view, parent, state)
            outRect.bottom = verticalSpacing
        }
    }
}