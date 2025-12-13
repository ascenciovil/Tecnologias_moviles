package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class PendingUploadsAdapter(
    private val onRetry: (pendingId: Long) -> Unit,
    private val onDelete: (pendingId: Long) -> Unit
) : RecyclerView.Adapter<PendingUploadsAdapter.VH>() {

    private val items = mutableListOf<PendingUploadUi>()

    fun submit(newItems: List<PendingUploadUi>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tv_pending_title)
        val tvSub: TextView = itemView.findViewById(R.id.tv_pending_sub)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_pending_status)
        val tvDate: TextView = itemView.findViewById(R.id.tv_pending_date)
        val tvError: TextView = itemView.findViewById(R.id.tv_pending_error)
        val btnRetry: MaterialButton = itemView.findViewById(R.id.btn_pending_retry)
        val btnDelete: MaterialButton = itemView.findViewById(R.id.btn_pending_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pending_upload, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.tvTitle.text = item.nombre
        holder.tvSub.text = item.descripcion
        holder.tvDate.text = item.createdAtLabel
        holder.tvStatus.text = when (item.status) {
            "PENDING" -> "Pendiente"
            "UPLOADING" -> "Subiendo…"
            "FAILED" -> "Falló"
            else -> item.status
        }

        val showError = item.status == "FAILED" && !item.lastError.isNullOrBlank()
        holder.tvError.visibility = if (showError) View.VISIBLE else View.GONE
        holder.tvError.text = if (showError) "Error: ${item.lastError}" else ""

        holder.btnRetry.setOnClickListener { onRetry(item.id) }
        holder.btnDelete.setOnClickListener { onDelete(item.id) }
    }

    override fun getItemCount(): Int = items.size
}
