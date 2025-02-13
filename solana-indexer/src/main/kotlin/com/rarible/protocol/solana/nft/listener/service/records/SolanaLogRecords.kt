package com.rarible.protocol.solana.nft.listener.service.records

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.rarible.blockchain.scanner.solana.model.SolanaLog
import com.rarible.blockchain.scanner.solana.model.SolanaLogRecord
import com.rarible.protocol.solana.borsh.CreateMetadataAccountArgs

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
sealed class SolanaItemLogRecord : SolanaLogRecord() {
    data class InitializeMintRecord(
        val mint: String,
        val mintAuthority: String,
        val decimals: Int,
        override val log: SolanaLog
    ) : SolanaItemLogRecord() {
        override fun getKey(): String = mint
    }

    data class InitializeAccountRecord(
        val account: String,
        val mint: String,
        val owner: String,
        override val log: SolanaLog
    ) : SolanaItemLogRecord() {
        override fun getKey(): String = mint
    }

    data class MintToRecord(
        val account: String,
        val mintAmount: Long,
        val mint: String,
        override val log: SolanaLog
    ) : SolanaItemLogRecord() {
        override fun getKey(): String = mint
    }

    data class BurnRecord(
        val account: String,
        val burnAmount: Long,
        val mint: String,
        override val log: SolanaLog
    ) : SolanaItemLogRecord() {
        override fun getKey(): String = mint
    }

    data class TransferRecord(
        val mint: String,
        val from: String,
        val to: String,
        val amount: Long,
        override val log: SolanaLog
    ) : SolanaItemLogRecord() {
        override fun getKey(): String = mint
    }

    data class CreateMetadataRecord(
        val mint: String,
        val metadata: CreateMetadataAccountArgs,
        override val log: SolanaLog
    ) : SolanaItemLogRecord() {
        override fun getKey(): String = mint
    }
}