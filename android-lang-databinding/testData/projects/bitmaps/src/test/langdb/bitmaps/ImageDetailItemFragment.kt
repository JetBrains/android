package test.langdb.bitmaps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import test.langdb.bitmaps.databinding.FragmentImageDetailItemBinding

class ImageDetailItemFragment(private val imageIndex: Int) : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding: FragmentImageDetailItemBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_image_detail_item, container, false)
        binding.image = Image(imageIndex)
        binding.imageListener = object : ImageListener {
            override fun onLoadFailed() {
                binding.imageProgress.visibility = View.GONE
            }

            override fun onLoaded() {
                binding.imageProgress.visibility = View.GONE
            }
        }

        return binding.root
    }
}
