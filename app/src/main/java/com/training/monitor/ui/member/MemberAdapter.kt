// 대원 목록 RecyclerView 어댑터

package com.training.monitor.ui.member

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.training.monitor.data.model.MemberDto
import com.training.monitor.databinding.ItemMemberBinding

class MemberAdapter : ListAdapter<MemberDto, MemberAdapter.ViewHolder>(DiffCallback) {

    var onItemClick: ((MemberDto) -> Unit)? = null

    inner class ViewHolder(private val binding: ItemMemberBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(member: MemberDto) {
            binding.tvName.text = "${member.rank ?: ""} ${member.name}".trim()
            binding.tvMilitaryId.text = member.militaryId
            binding.tvUnit.text = member.unitName ?: "-"
            binding.root.setOnClickListener { onItemClick?.invoke(member) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<MemberDto>() {
        override fun areItemsTheSame(a: MemberDto, b: MemberDto) = a.id == b.id
        override fun areContentsTheSame(a: MemberDto, b: MemberDto) = a == b
    }
}
