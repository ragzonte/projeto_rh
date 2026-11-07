# Satélite Check

App Android que verifica se o seu celular e a sua operadora têm suporte a recursos de satélite
(SOS e mensagens via satélite, como nos Galaxy S25 e Pixel 9) e mostra, em tempo real, se o aparelho
está conectado por satélite.

## O que o app faz

- **Veredito**: resume se há suporte a satélite, por ordem de força do sinal:
  conexão ativa → operadora → fabricante.
- **Conexão agora** (Android 15+): avisa quando o aparelho está registrado numa rede celular por
  satélite (`ServiceState.isUsingNonTerrestrialNetwork()`) ou quando alguma rede de dados usa
  satélite (`NetworkCapabilities.TRANSPORT_SATELLITE`).
- **Operadora** (por chip): lê os indicadores de satélite da configuração da operadora
  (`CarrierConfigManager`): conexão a satélite habilitada, redes de satélite cadastradas,
  verificação de plano e SOS via satélite.
- **Aparelho**: fabricante, modelo, versão do Android e do modem, e se o fabricante configurou um
  serviço de satélite no sistema (recurso `config_satellite_service_package`).
- Atalho para as configurações de rede, onde ficam as opções de satélite quando existem.

## O que o app não faz e por quê

Nenhum app consegue "desbloquear" satélite. O recurso depende de:

1. **Hardware e sistema**: modem compatível com redes não terrestres (NTN) e integração do fabricante.
2. **Operadora**: acordo com um provedor de satélite (Skylo, Starlink etc.) e, muitas vezes, um plano.

As APIs que controlam o satélite no Android (`SatelliteManager`) são restritas a apps de sistema, e as
configurações da operadora só podem ser alteradas por ela. Apps que prometem ativar satélite em
qualquer celular são falsos. O Satélite Check apenas **lê** o que o sistema informa. Ele não pede
acesso à internet e não envia dados para lugar nenhum.

## Permissões

| Permissão | Uso |
|---|---|
| `READ_PHONE_STATE` | Listar os chips e ler a configuração de satélite da operadora (opcional) |
| `ACCESS_NETWORK_STATE` | Detectar redes de dados via satélite |

## Como compilar

Requisitos: JDK 17 e Android SDK 35 (ou Android Studio).

```bash
cd SateliteCheck
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

Cada push que altera esta pasta roda o workflow **Satélite Check (Android)** no GitHub Actions.
O APK pronto para instalar fica no artefato `satelite-check-apk` da execução.

## Estrutura

```
app/src/main/java/com/ragzonte/satelitecheck/
├── MainActivity.kt
├── model/      # dados e regra do veredito (Kotlin puro, com testes)
├── data/       # leitura do sistema: SatelliteInspector e SatelliteMonitor
└── ui/         # tela em Jetpack Compose
```
