package com.nobcdz.tvquiz

class GateActivity : MainActivity() {
    override fun onResume() {
        super.onResume()
        visible = !isFinishing
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    companion object {
        internal var visible = false
            private set
    }
}

