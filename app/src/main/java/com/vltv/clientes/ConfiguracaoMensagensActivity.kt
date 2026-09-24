package com.vltv.clientes

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vltv.clientes.databinding.ActivityConfiguracaoMensagensBinding
import com.vltv.clientes.network.AppConfig

class ConfiguracaoMensagensActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracaoMensagensBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracaoMensagensBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVoltar.setOnClickListener { finish() }

        binding.etMsg3Dias.setText(AppConfig.getMensagem3Dias(this))
        binding.etMsg2Dias.setText(AppConfig.getMensagem2Dias(this))
        binding.etMsg1Dia.setText(AppConfig.getMensagem1Dia(this))
        binding.etMsgVencido.setText(AppConfig.getMensagemVencido(this))

        binding.btnConfigBackend.setOnClickListener {
            startActivity(Intent(this, ConfiguracaoBackendActivity::class.java))
        }

        binding.btnSalvarMensagens.setOnClickListener {
            AppConfig.salvarMensagens(
                this,
                binding.etMsg3Dias.text.toString(),
                binding.etMsg2Dias.text.toString(),
                binding.etMsg1Dia.text.toString(),
                binding.etMsgVencido.text.toString()
            )
            binding.btnSalvarMensagens.text = "Salvo ✓"
            binding.btnSalvarMensagens.postDelayed({
                binding.btnSalvarMensagens.text = "Salvar"
            }, 1500)
        }
    }
}
