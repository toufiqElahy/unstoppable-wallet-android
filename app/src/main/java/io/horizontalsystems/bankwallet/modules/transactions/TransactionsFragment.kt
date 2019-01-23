package io.horizontalsystems.bankwallet.modules.transactions

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.annotation.NonNull
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.horizontalsystems.bankwallet.R
import io.horizontalsystems.bankwallet.core.setOnSingleClickListener
import io.horizontalsystems.bankwallet.modules.fulltransactioninfo.FullTransactionInfoModule
import io.horizontalsystems.bankwallet.modules.main.MainActivity
import io.horizontalsystems.bankwallet.modules.transactions.transactionInfo.TransactionInfoViewModel
import io.horizontalsystems.bankwallet.viewHelpers.DateHelper
import io.horizontalsystems.bankwallet.viewHelpers.HudHelper
import io.horizontalsystems.bankwallet.viewHelpers.ValueFormatter
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.fragment_transactions.*
import kotlinx.android.synthetic.main.transaction_info_bottom_sheet.*
import kotlinx.android.synthetic.main.view_holder_filter.*
import kotlinx.android.synthetic.main.view_holder_transaction.*

class TransactionsFragment : android.support.v4.app.Fragment(), TransactionsAdapter.Listener, FilterAdapter.Listener {

    private lateinit var viewModel: TransactionsViewModel
    private lateinit var transInfoViewModel: TransactionInfoViewModel
    private val transactionsAdapter = TransactionsAdapter(this)
    private val filterAdapter = FilterAdapter(this)
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transactions, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(TransactionsViewModel::class.java)
        viewModel.init()

        transactionsAdapter.viewModel = viewModel
        toolbar.setTitle(R.string.Transactions_Title)

        recyclerTransactions.adapter = transactionsAdapter
        recyclerTransactions.layoutManager = LinearLayoutManager(context)
        recyclerTags.adapter = filterAdapter
        recyclerTags.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        pullToRefresh.setOnRefreshListener {
            viewModel.delegate.refresh()
        }

        viewModel.filterItems.observe(this, Observer { filters ->
            filters?.let {
                filterAdapter.filters = it
                filterAdapter.notifyDataSetChanged()
            }
        })

        viewModel.showTransactionInfoLiveEvent.observe(this, Observer { transactionHash ->
            transactionHash?.let { transactHash ->
                transInfoViewModel.delegate.getTransaction(transactHash)
            }
        })

        viewModel.didRefreshLiveEvent.observe(this, Observer {
            pullToRefresh.isRefreshing = false
        })

        viewModel.reloadLiveEvent.observe(this, Observer {
            transactionsAdapter.notifyDataSetChanged()

            recyclerTransactions.visibility = if (viewModel.delegate.itemsCount == 0) View.GONE else View.VISIBLE
            emptyListText.visibility = if (viewModel.delegate.itemsCount == 0) View.VISIBLE else View.GONE
        })

        setBottomSheet()
    }

    //Bottom sheet shows TransactionInfo
    private fun setBottomSheet() {

        bottomSheetBehavior = BottomSheetBehavior.from(nestedScrollView)

        transactionsDim.visibility = View.GONE
        transactionsDim.alpha = 0f

        var bottomSheetSlideOffOld = 0f

        bottomSheetBehavior?.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(@NonNull bottomSheet: View, newState: Int) {}

            override fun onSlide(@NonNull bottomSheet: View, slideOffset: Float) {
                transactionsDim.alpha = slideOffset
                if (bottomSheetSlideOffOld >= 0.7 && slideOffset < 0.7) {
                    (activity as? MainActivity)?.setBottomNavigationVisible(true)
                } else if (bottomSheetSlideOffOld >= 0.8 && slideOffset > 0.9) {
                    (activity as? MainActivity)?.setBottomNavigationVisible(false)
                }

                transactionsDim.visibility = if (slideOffset == 0f) View.GONE else View.VISIBLE

                bottomSheetSlideOffOld = slideOffset
            }
        })

        transInfoViewModel = ViewModelProviders.of(this).get(TransactionInfoViewModel::class.java)
        transInfoViewModel.init()

        transactionIdView.setOnClickListener { transInfoViewModel.delegate.onCopyId() }
        txtFullInfo.setOnClickListener { transInfoViewModel.delegate.showFullInfo() }
        transactionsDim.setOnClickListener { bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED }

        transInfoViewModel.showCopiedLiveEvent.observe(this, Observer {
            HudHelper.showSuccessMessage(R.string.Hud_Text_Copied)
        })

        transInfoViewModel.showFullInfoLiveEvent.observe(this, Observer { pair ->
            pair?.let {
                activity?.let { activity ->
                    FullTransactionInfoModule.start(activity, transactionHash = it.first, coinCode = it.second)
                }
            }
        })

        transInfoViewModel.transactionLiveData.observe(this, Observer { txRecord ->
            txRecord?.let { txRec ->
                (activity as? MainActivity)?.setBottomNavigationVisible(false)
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED

                fiatValue.apply {
                    text = txRec.currencyValue?.let { ValueFormatter.format(it, showNegativeSign = true, realNumber = true) }
                    setTextColor(resources.getColor(if (txRec.incoming) R.color.green_crypto else R.color.yellow_crypto, null))
                }

                coinValue.apply {
                    text = ValueFormatter.format(txRec.coinValue, true, true)
                }

                itemTime.apply {
                    bindTime(title = getString(R.string.TransactionInfo_Time), time = txRec.date?.let { DateHelper.getFullDateWithShortMonth(it) } ?: "")
                }

                itemStatus.apply {
                    bindStatus(txRec.status)
                }

                transactionIdView.bindTransactionId(txRec.transactionHash)

                itemFrom.apply {
                    setOnClickListener { transInfoViewModel.delegate.onCopyFromAddress() }
                    visibility = if (txRec.from.isNullOrEmpty()) View.GONE else View.VISIBLE
                    bindAddress(title = getString(R.string.TransactionInfo_From), address = txRec.from, showBottomBorder = true)
                }

                itemTo.apply {
                    setOnClickListener { transInfoViewModel.delegate.onCopyToAddress() }
                    visibility = if (txRec.to.isNullOrEmpty()) View.GONE else View.VISIBLE
                    bindAddress(title = getString(R.string.TransactionInfo_To), address = txRec.to, showBottomBorder = true)
                }
            }
        })
    }

    override fun onItemClick(item: TransactionViewItem) {
        viewModel.delegate.onTransactionItemClick(item)
    }

    override fun onFilterItemClick(item: TransactionFilterItem) {
        viewModel.delegate.onFilterSelect(item.adapterId)
    }

    fun onBackPressed(): Boolean {
        if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
            return true
        }
        return false
    }

}


class TransactionsAdapter(private var listener: Listener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onItemClick(item: TransactionViewItem)
    }

    lateinit var viewModel: TransactionsViewModel

    override fun getItemCount() = viewModel.delegate.itemsCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_holder_transaction, parent, false)

        return ViewHolderTransaction(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ViewHolderTransaction -> {
                val transactionRecord = viewModel.delegate.itemForIndex(position)
                holder.bind(transactionRecord) { listener.onItemClick(transactionRecord) }
            }
        }
    }

}

class ViewHolderTransaction(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {

    fun bind(transactionRecord: TransactionViewItem, onClick: () -> (Unit)) {

        containerView.setOnSingleClickListener { onClick.invoke() }
        txValueInFiat.text = transactionRecord.currencyValue?.let { ValueFormatter.formatForTransactions(it, transactionRecord.incoming) }
        txValueInCoin.text = ValueFormatter.format(transactionRecord.coinValue, true)
        txDate.text = transactionRecord.date?.let { DateHelper.getShortDateForTransaction(it) }
        val time = transactionRecord.date?.let { DateHelper.getOnlyTime(it) }
        txStatusWithTimeView.bind(transactionRecord.status, time)
    }
}

class FilterAdapter(private var listener: Listener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onFilterItemClick(item: TransactionFilterItem)
    }

    private var selectedFilterId: String? = null

    private val firstTag = TransactionFilterItem(null, "All")
    var filters: List<TransactionFilterItem> = listOf()

    private val allFilters: MutableList<TransactionFilterItem>
        get() {
            val items = mutableListOf(firstTag)
            items.addAll(filters)
            return items
        }

    override fun getItemCount() = allFilters.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            ViewHolderFilter(LayoutInflater.from(parent.context).inflate(R.layout.view_holder_filter, parent, false))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ViewHolderFilter -> holder.bind(
                    allFilters[position].name,
                    active = selectedFilterId == allFilters[position].adapterId,
                    firstButton = position == 0) {
                listener.onFilterItemClick(allFilters[position])
                selectedFilterId = allFilters[position].adapterId
                notifyDataSetChanged()
            }
        }
    }

}

class ViewHolderFilter(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {

    fun bind(filterName: String, active: Boolean, firstButton: Boolean, onClick: () -> (Unit)) {
        filter_text.setOnClickListener { onClick.invoke() }

        if (firstButton) {
            val localizedFirstButtonTitle = containerView.context.getString(R.string.Transactions_FilterAll)
            filter_text.text = localizedFirstButtonTitle
        } else {
            filter_text.text = filterName
        }
        filter_text.isActivated = active
    }
}
