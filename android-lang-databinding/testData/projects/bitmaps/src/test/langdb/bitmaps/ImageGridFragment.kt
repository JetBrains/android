package test.langdb.bitmaps

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.NavHost
import com.bumptech.glide.Glide
import test.langdb.bitmaps.databinding.FragmentImageGridBinding
import test.langdb.bitmaps.databinding.FragmentImageGridItemBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ImageGridFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding: FragmentImageGridBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_image_grid, container, false)
        binding.imageGrid.adapter = ImageAdapter(binding.root.context, ImageGridClickHandler(activity as NavHost))
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.bitmaps_grid_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val context = requireContext()
        when (item.itemId) {
            R.id.bitmaps_clear_cache -> {
                GlobalScope.launch { Glide.get(context).clearDiskCache() } // Has to run on background thread
                Glide.get(context).clearMemory()
                Toast.makeText(activity, R.string.bitmaps_clear_cache_completed, Toast.LENGTH_SHORT).show()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }
}

class ImageGridClickHandler(private val navHost: NavHost) {
    fun handleImageClicked(image: Image) {
        navHost.navController.navigate(R.id.action_nav_to_image_detail, bundleOf("imageIndex" to image.index))
    }
}

private class ImageAdapter(private val context: Context, private val imageGridClickHandler: ImageGridClickHandler) : BaseAdapter() {
    override fun getCount(): Int = imageEntries.size

    override fun getItemViewType(position: Int) = R.layout.fragment_image_grid_item

    override fun getItem(position: Int): Any? = null
    override fun getItemId(position: Int): Long = 0L

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding: FragmentImageGridItemBinding
        val finalView: LinearLayout
        if (convertView == null) {
            binding = FragmentImageGridItemBinding.inflate(LayoutInflater.from(context))
            binding.clickHandler = imageGridClickHandler
            binding.imageLayout.tag = binding
            finalView = binding.imageLayout
        } else {
            finalView = convertView as LinearLayout
            binding = finalView.tag as FragmentImageGridItemBinding
        }

        binding.image = Image(position)
        return finalView
    }
}

