package com.vltv.clientes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vltv.clientes.databinding.ActivityConfiguracaoBackendBinding
import com.vltv.clientes.network.AppConfig
import com.vltv.clientes.network.BackendApi
import com.vltv.clientes.network.EnvioResultado
import kotlinx.coroutines.launch

class ConfiguracaoBackendActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracaoBackendBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracaoBackendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVoltar.setOnClickListener { finish() }

        binding.etUrlBackend.setText(AppConfig.getBackendUrl(this))
        binding.etApiKey.setText(AppConfig.getApiKey(this))

        binding.btnTestar.setOnClickListener { testarConexao() }

        binding.btnSalvarBackend.setOnClickListener {
            AppConfig.salvarBackend(
                this,
                binding.etUrlBackend.text.toString(),
                binding.etApiKey.text.toString()
            )
            binding.btnSalvarBackend.text = "Salvo ✓"
            binding.btnSalvarBackend.postDelayed({
                binding.btnSalvarBackend.text = "Salvar"
                finish()
            }, 1000)
        }
    }

    private fun testarConexao() {
        val url = binding.etUrlBackend.text.toString().trim()
        if (url.isBlank()) {
            binding.tvResultadoTeste.text = "Informe a URL do servidor primeiro."
            binding.tvResultadoTeste.setTextColor(resources.getColor(R.color.vermelho_critico, theme))
            return
        }

        binding.tvResultadoTeste.text = "Testando..."
        binding.tvResultadoTeste.setTextColor(resources.getColor(R.color.cinza_texto, theme))

        lifecycleScope.launch {
            when (val resultado = BackendApi.testarConexao(url)) {
                is EnvioResultado.Ok -> {
                    binding.tvResultadoTeste.text = "✓ Conectado! O bot de WhatsApp está pronto."
                    binding.tvResultadoTeste.setTextColor(resources.getColor(R.color.verde_ok, theme))
                }
                is EnvioResultado.Falha -> {
                    binding.tvResultadoTeste.text = "✗ ${resultado.motivo}"
                    binding.tvResultadoTeste.setTextColor(resources.getColor(R.color.vermelho_critico, theme))
                }
            }
        }
    }
}
