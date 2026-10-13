package com.vltv.clientes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vltv.clientes.databinding.ActivityConfiguracaoBackendBinding
import com.vltv.clientes.network.AppConfig
import com.vltv.clientes.network.BackendApi
import com.vltv.clientes.network.EnvioResultado
import com.vltv.clientes.network.PainelApi
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
        binding.btnEnviarPainel.setOnClickListener { enviarParaPainel() }

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

    // Migração única: manda todos os clientes deste aparelho para o painel Admin.
    private fun enviarParaPainel() {
        val usuario = binding.etPainelUsuario.text.toString().trim()
        val senha = binding.etPainelSenha.text.toString()
        if (usuario.isBlank() || senha.isBlank()) {
            binding.tvResultadoPainel.text = "Informe o usuário e a senha do painel."
            binding.tvResultadoPainel.setTextColor(resources.getColor(R.color.vermelho_critico, theme))
            return
        }
        binding.btnEnviarPainel.isEnabled = false
        binding.tvResultadoPainel.text = "Enviando..."
        binding.tvResultadoPainel.setTextColor(resources.getColor(R.color.cinza_texto, theme))
        lifecycleScope.launch {
            val resposta = PainelApi.enviarClientes(this@ConfiguracaoBackendActivity, usuario, senha)
            binding.tvResultadoPainel.text = resposta
            val cor = if (resposta.startsWith("✓")) R.color.verde_ok else R.color.vermelho_critico
            binding.tvResultadoPainel.setTextColor(resources.getColor(cor, theme))
            binding.btnEnviarPainel.isEnabled = true
        }
    }
}
