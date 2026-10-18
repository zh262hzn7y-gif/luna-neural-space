# Rota dos Parceiros — Especificação de Funcionalidades

> Documento de especificação (somente documentação). Nenhum código do app foi alterado neste commit.
> App: `space.neural.luna` · Versão de referência: **6.2.1** · Data: 18/10/2026

---

## 1. Visão geral

O **Rota dos Parceiros** é um app Android para motoristas parceiros de **Governador Valadares (MG)**. O objetivo desta evolução é transformar o app em uma "sala única" da cidade: todos os usuários aparecem num mapa em tempo real, conversam por texto/áudio/ligação (estilo walkie-talkie), podem pedir socorro com localização seguível e reportar acidentes e trânsito pesado. Também entra um redesign visual completo com a paleta do app.

## 2. Estado atual (o que já existe)

Levantamento feito a partir do repositório e do APK 6.2.1 publicado:

| Item | Situação atual |
|---|---|
| Plataforma | App nativo **Kotlin** com **WebView** carregando `assets/www/index.html`; ponte JS ↔ nativo via **`LunaBridge`** (`LunaBridge.kt`, `LunaRuntime.kt`). |
| Versão no `main` | `versionCode 86`, commit `4d5e411` (`build.gradle` ainda declara `versionName "6.2.0"`). |
| Versão 6.2.1 | Publicada como release (`RotaDosParceiros-6.2.1.apk`), abas **Início / Rota dos Parceiros / Ajustes**. **O `index.html` da 6.2.1 NÃO está commitado no `main`** — o `main` tem uma versão anterior. |
| Sala de voz | **WebRTC** (`RTCPeerConnection`, STUN `stun.l.google.com:19302`, sem TURN) com sinalização **MQTT sobre WebSocket** no broker público `wss://broker.emqx.io:8084/mqtt`. Entrada por código combinado. |
| LiveKit | **Não está no APK.** Aviso no app: o rádio LiveKit na nuvem depende de chave secreta que fica só no servidor e o login por e-mail ainda não abre a sala. |
| Mapa | Iframe de embed do **OpenStreetMap** (`openstreetmap.org/export/embed.html` com bbox), altura fixa ~240 px. Não há marcadores de outros usuários. |
| Clima | **Open-Meteo** (lat -18.851, lon -41.949, fuso America/Sao_Paulo). |
| IA | **Gemini** (Google) citado no código do repositório. |
| Permissões Android | `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`. **Não há** `ACCESS_BACKGROUND_LOCATION` nem serviço `foregroundServiceType="location"`. |
| Serviço em primeiro plano | `OverlayService` (`specialUse`) — bolha flutuante de controle de turno. |
| Build | GitHub Actions `.github/workflows/build-apk.yml`: JDK 17, Gradle 8.10.2, `assembleDebug`, artefato `luna-apk`. Dispara em todo push no `main` e em `workflow_dispatch`. |
| SDK | `minSdk 24`, `targetSdk 34`, `compileSdk 34`. |

### 2.1 Pré-requisito antes de implementar

1. **Commitar o código da 6.2.1 no `main`** (o `index.html` extraído do APK 6.2.1 e quaisquer mudanças nativas), atualizando `versionName` para `6.2.1`. Sem isso, qualquer implementação partirá de uma base antiga e a 6.2.1 será perdida.
2. Trabalhar em branch separada e abrir pull request; o Actions gera o APK para teste.

---

## 3. Funcionalidades

### F1 — Mapa só de Governador Valadares com localização em tempo real

**Descrição.** O mapa mostra apenas a área de Governador Valadares e a posição em tempo real de cada usuário conectado, na rua onde ele está.

**Requisitos / critérios de aceite**
- O mapa abre centralizado em Governador Valadares e **não permite sair** dos limites da cidade (`maxBounds` + zoom mínimo).
- Cada usuário online aparece como marcador (avatar/inicial + nome) na posição atual.
- A posição se atualiza a cada poucos segundos (alvo: ≤ 5 s com o app em uso).
- Opcional: mostrar o nome da rua atual do usuário (geocodificação reversa) — ver questão em aberto.
- Usuário offline há mais de X minutos some do mapa ou fica esmaecido.

**Notas técnicas**
- Substituir o iframe de embed do OSM por uma biblioteca de mapa interativo dentro da WebView (ex.: **Leaflet** ou MapLibre GL), que permite marcadores, limites e camadas.
- Limites aproximados de GV devem ser definidos (bbox) — **valor a confirmar**.
- Posição vem do GPS nativo via `LunaBridge` (mais preciso que `navigator.geolocation` na WebView) e é publicada no canal de tempo real (ver F3).
- Geocodificação reversa (nome da rua): Nominatim tem política de uso restrita (máx. ~1 req/s, sem uso massivo); para muitos usuários avaliar cache ou serviço próprio.
- Privacidade: deixar claro ao usuário que a localização é compartilhada com todos da sala; prever opção de pausar compartilhamento.

### F2 — Card à direita do mapa: texto, áudio e ligação (walkie-talkie)

**Descrição.** Ao lado direito do mapa fica um card de comunicação com mensagens de texto, mensagens de áudio e ligação. O áudio funciona estilo walkie-talkie (aperta para falar) e todos que estão na ligação ouvem.

**Requisitos / critérios de aceite**
- Layout: mapa à esquerda, card à direita (no tablet deitado). Em telas estreitas o card vira painel inferior/aba.
- Texto: enviar e receber mensagens em tempo real, com nome, horário e histórico recente.
- Áudio gravado: gravar, enviar e reproduzir mensagens de voz curtas.
- Ligação em grupo: botão **"Segure para falar" (PTT)**; enquanto pressionado, o áudio do usuário vai para todos na ligação; indicador de quem está falando.
- Todos os participantes da ligação ouvem simultaneamente.

**Notas técnicas**
- A sala atual é WebRTC ponto a ponto. Para muitos participantes, malha P2P não escala (cada um envia áudio para todos); um **SFU como LiveKit** é o caminho indicado para "todos ouvem".
- LiveKit exige **token gerado no servidor** (a API secret não pode ir no APK) — é necessário um backend/endpoint de tokens (ver F10).
- Sem servidor TURN, parte das conexões em rede móvel (4G/CGNAT) pode falhar no WebRTC P2P; LiveKit Cloud resolve isso.
- Mensagens de áudio gravadas precisam de armazenamento (não cabem bem em MQTT público) — **definir onde armazenar**.
- Permissão `RECORD_AUDIO` já existe.

### F3 — Sala única de Valadares (entrada automática)

**Descrição.** Existe uma única sala para Governador Valadares. Quem baixa o app entra direto e já pode falar ou mandar mensagem, sem código.

**Requisitos / critérios de aceite**
- Ao abrir o app (após permissões e um nome/apelido), o usuário entra automaticamente na sala "Valadares".
- Não há tela de código de sala para o fluxo principal.
- Usuário consegue mandar texto e usar PTT imediatamente.

**Notas técnicas**
- Hoje a sala usa código combinado e tópico MQTT derivado; trocar para um tópico/sala fixo (ex.: `rota/gv`).
- Identificação mínima do usuário (nome + id do aparelho) — **definir se haverá login** (o aviso atual cita login por e-mail).
- Moderação/abuso: sala aberta a qualquer pessoa que baixar o APK — **definir regras (bloquear/denunciar)**.

### F4 — Botão de socorro

**Descrição.** Botão de emergência que notifica todos os usuários e mostra a localização em tempo real de quem pediu socorro, seguível como GPS.

**Requisitos / critérios de aceite**
- Botão de socorro sempre visível, com confirmação (ex.: segurar 2 s) para evitar acionamento acidental.
- Ao acionar: todos os usuários recebem **notificação** (inclusive com app em segundo plano) com nome e local.
- O marcador de quem pediu socorro fica destacado no mapa e se atualiza em tempo real.
- Botão "Seguir" abre navegação até o usuário (rota no próprio mapa e/ou intent para Google Maps/Waze com a coordenada).
- Quem pediu pode encerrar o socorro; o alerta some para todos.

**Notas técnicas**
- Ver seção 4 (localização em segundo plano e foreground service).
- Notificações com o app fechado exigem **push (ex.: Firebase Cloud Messaging)** ou serviço em primeiro plano mantendo conexão; MQTT/WebSocket na WebView não recebe nada com o app morto. **Decisão em aberto.**
- `POST_NOTIFICATIONS` já declarado (Android 13+ precisa pedir em tempo de execução).

### F5 — Relatórios de acidente e fluxo pesado

**Descrição.** O usuário reporta, na sua localização atual, acidente ou trânsito pesado; o alerta aparece para todos no mapa.

**Requisitos / critérios de aceite**
- Botões rápidos: **Acidente** e **Fluxo pesado** (1 toque, usa a posição atual).
- O alerta aparece no mapa com ícone, horário e autor para todos.
- Alertas expiram automaticamente (ex.: 30–60 min — **valor a definir**) ou podem ser confirmados/removidos por outros.

**Notas técnicas**
- Publicar no canal da sala (tópico separado, ex.: `rota/gv/alertas`). Para persistir e mostrar alertas a quem entra depois, MQTT `retain` ajuda, mas um backend é mais confiável.

### F6 — Km rodados por dia e precisão do GPS

**Descrição.** Monitorar os quilômetros rodados por dia e melhorar a precisão do GPS.

**Requisitos / critérios de aceite**
- Contador de km do dia visível (integrar com cronômetro/turno no topo — F9).
- Histórico por dia (pelo menos últimos 30 dias) salvo no aparelho.
- Cálculo ignora "saltos" de GPS e ruído com o carro parado.

**Notas técnicas**
- Usar GPS nativo (`FusedLocationProviderClient`, prioridade alta) via `LunaBridge` em vez de `navigator.geolocation`.
- Filtrar pontos com `accuracy` ruim (ex.: > 30 m), velocidades impossíveis e deslocamentos mínimos; somar distância Haversine entre pontos válidos.
- Contagem com tela apagada exige foreground service de localização (seção 4).
- Adicionar dependência `com.google.android.gms:play-services-location` (hoje não está no `build.gradle`).

### F7 — Visual do mapa verde e amarelo (estilo brasileiro)

**Descrição.** Mapa com visual verde e amarelo, estilo brasileiro, ruas bonitas.

**Requisitos / critérios de aceite**
- Base de mapa limpa e legível, com acentos verde e amarelo (marcadores, rota, destaques de ruas/áreas).
- Combinar com o redesign (F8) — mapa claro dentro de interface escura, ou variante escura, **a decidir**.

**Notas técnicas**
- Opções consideradas: **CartoDB Positron** (base clara e neutra, boa para colorir por cima) e **Stamen Terrain**. Observação: os tiles da Stamen hoje são servidos pela **Stadia Maps**, que exige cadastro/chave e tem limites; CARTO também tem termos de uso e atribuição obrigatória.
- Verde/amarelo pode vir de filtro CSS sobre os tiles, de camadas próprias (ruas principais destacadas) ou de um estilo vetorial customizado (MapLibre) — **a definir**.
- Manter atribuição © OpenStreetMap contributors.

### F8 — Redesign da página com a paleta do app

**Descrição.** Redesenhar a interface usando a paleta oficial.

| Nome | Hex |
|---|---|
| Obsidiana (fundo) | `#07060E` |
| Violeta elétrico (primária) | `#8D6BFF` |
| Ciano gelo (destaque/info) | `#49E0FF` |
| Esmeralda (sucesso/online) | `#3EE0A0` |
| Branco gelo (texto) | `#F3F0FF` |

**Requisitos / critérios de aceite**
- Todas as telas (Início, Rota dos Parceiros, Ajustes) usam somente a paleta (+ tons derivados com transparência).
- Cores definidas como variáveis CSS (`--obsidiana`, `--violeta`, …) num único lugar.
- Contraste de texto adequado (WCAG AA) sobre o fundo obsidiana.
- Socorro usa uma cor de alerta clara — **definir se entra vermelho fora da paleta** (questão em aberto).
- Não alterar lógica de negócio, apenas visual/layout.

### F9 — Pedidos já existentes (`ISSUES.md` e `luna-modificacoes/README.md`)

**Requisitos / critérios de aceite**
- **Layout responsivo com tablet deitado (paisagem) como base**; adaptar para celular em pé.
- **Cronômetro no topo** (turno), sempre visível; junto com km do dia (F6).
- **Cards redesenhados** com a nova paleta.
- **Foto nova da Luna** — **arquivo da foto ainda precisa ser fornecido/confirmado**.

**Notas técnicas**
- O `MainActivity` já trata `configChanges` de orientação; o layout é responsabilidade do `index.html` (CSS grid/media queries).

### F10 — Chaves e configuração (LiveKit e outras)

O usuário informou que já enviou chaves. **Nenhum valor de chave é escrito neste documento nem deve ser commitado no repositório.** Nomes previstos:

| Nome | Uso | Onde fica |
|---|---|---|
| `LIVEKIT_URL` | URL do projeto LiveKit Cloud (wss://…) | Pode ir no app (não é segredo) ou via servidor |
| `LIVEKIT_API_KEY` | Gerar tokens de acesso | **Somente servidor / GitHub Actions secret** |
| `LIVEKIT_API_SECRET` | Assinar tokens de acesso | **Somente servidor / GitHub Actions secret — nunca no APK** |
| `GEMINI_API_KEY` | IA (Gemini) | Servidor/secret (idealmente proxy no servidor) |
| `MAP_TILES_API_KEY` (se usar Stadia/outro provedor) | Tiles do mapa | Secret / config de build |
| `FCM` / `google-services.json` (se usar push para F4) | Notificações | Secret de CI |

**Regras**
- Guardar como **GitHub Actions secrets** (Settings → Secrets and variables → Actions) ou no servidor; injetar no build apenas o que for público.
- Qualquer chave que vá dentro do APK pode ser extraída — segredos (`*_SECRET`, chaves de API pagas) ficam no servidor.
- Observação de segurança: o `build.gradle` atual contém senhas do keystore em texto; recomenda-se movê-las para secrets.

---

## 4. Notas técnicas gerais

### 4.1 Localização em segundo plano (socorro, mapa, km)
- Adicionar `ACCESS_BACKGROUND_LOCATION` (Android 10+) — no Android 11+ precisa ser pedida **separadamente**, depois da localização em primeiro plano, e o usuário escolhe "Permitir o tempo todo" nas configurações.
- Criar um **foreground service** com `android:foregroundServiceType="location"` e a permissão `FOREGROUND_SERVICE_LOCATION` (Android 14 / targetSdk 34), com notificação persistente ("Rota dos Parceiros compartilhando sua localização").
- O `OverlayService` atual é `specialUse`; a localização precisa do tipo `location` (no mesmo serviço com tipos combinados ou em serviço separado).
- Distribuição pela Play Store exige justificativa de localização em segundo plano; via APK direto não há essa revisão, mas a permissão continua obrigatória.
- Economia de bateria: alguns fabricantes matam serviços; orientar o usuário a desativar otimização de bateria para o app.

### 4.2 Aproveitar WebRTC / EMQX existentes
- A implementação atual (WebRTC + sinalização MQTT em `wss://broker.emqx.io:8084/mqtt`) pode servir de base para posições, texto e alertas no MVP.
- **Limites do broker público EMQX:** é um broker de teste, gratuito e **público** — qualquer pessoa que souber o tópico pode ler e publicar (localizações, mensagens e socorros ficariam expostos), não tem SLA e pode ter limites de conexão/mensagens. Hoje o app conecta com credenciais de teste genéricas.
- Recomendação: para produção usar broker próprio/privado com autenticação (ex.: EMQX Cloud/Serverless) ou backend próprio, e/ou criptografar payloads.
- Voz em grupo: migrar de P2P para **LiveKit** (SFU) quando o servidor de tokens existir; manter o P2P atual como alternativa.

### 4.3 Build
- O workflow `build-apk.yml` roda em todo push no `main`; gera APK **debug** (`app-debug.apk`) como artefato. Para releases, avaliar `assembleRelease` com assinatura via secrets.

---

## 5. Questões em aberto
1. Qual é o segundo nome de ferramenta que o usuário lembrava além do LiveKit? (não identificado no repositório)
2. Haverá backend próprio (tokens LiveKit, push, armazenamento de áudio e alertas)? Onde hospedar?
3. Login: por e-mail (como cita o aviso atual), apenas apelido, ou outro?
4. Limites exatos (bbox) de Governador Valadares para o mapa.
5. Provedor de tiles final (CARTO Positron, Stadia/Stamen Terrain ou estilo vetorial próprio) e forma do verde-amarelo.
6. Mostrar o nome da rua de cada usuário (geocodificação reversa) — necessário ou só o marcador basta?
7. Push para socorro com app fechado: usar Firebase Cloud Messaging?
8. Cor do botão de socorro: vermelho fora da paleta é aceitável?
9. Arquivo da nova foto da Luna.
10. Tempo de expiração de alertas de acidente/fluxo e de usuários inativos no mapa.
