package test.langdb.bitmaps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import test.langdb.bitmaps.databinding.FragmentImageDetailBinding

class ImageDetailFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding: FragmentImageDetailBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_image_detail, container, false)
        binding.imagePager.adapter = ImagePagerAdapter(fragmentManager!!)
        binding.imagePager.currentItem = arguments!!.getInt("imageIndex")
        binding.imagePager.offscreenPageLimit = 2
        return binding.root
    }

    private class ImagePagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
        override fun getCount(): Int {
            return imageEntries.size
        }

        override fun getItem(position: Int): Fragment {
            return ImageDetailItemFragment(position)
        }
    }

}
