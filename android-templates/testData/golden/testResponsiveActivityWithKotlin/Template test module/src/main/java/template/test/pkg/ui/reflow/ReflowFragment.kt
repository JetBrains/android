package template.test.pkg.ui.reflow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import template.test.pkg.databinding.FragmentReflowBinding

class ReflowFragment : Fragment() {

    private var _binding: FragmentReflowBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val reflowViewModel =
            ViewModelProvider(this).get(ReflowViewModel::class.java)

        _binding = FragmentReflowBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textReflow
        reflowViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}