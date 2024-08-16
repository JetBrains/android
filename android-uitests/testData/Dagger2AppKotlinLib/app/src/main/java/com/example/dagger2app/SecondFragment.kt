package com.example.dagger2app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment

class SecondFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_second, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val myArg = SecondFragmentArgs.fromBundle(arguments!!).myArg
        val textView = view.findViewById<TextView>(R.id.textview_second)
        textView.text = getString(R.string.hello_second_fragment, myArg)

        view.findViewById<View>(R.id.button_second).setOnClickListener {
            NavHostFragment.findNavController(this@SecondFragment)
                    .navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
    }
}
