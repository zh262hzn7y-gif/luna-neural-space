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

## 5. Lógica da jornada (quatro turnos)

Esta seção descreve o funcionamento do cronômetro de jornada (exibido no topo — ver F9). As regras abaixo foram passadas pelo usuário; pontos ambíguos estão em **5.7 Questões em aberto da jornada** e não foram decididos aqui.

### 5.1 Regras

1. Não há horários fixos — os turnos são sequenciais.
2. Ao clicar em **iniciar**, abre o primeiro turno.
3. Ao clicar em **fechar turno**, fecha o turno atual e avança automaticamente para o próximo: fecha o 1º vai pro 2º, fecha o 2º vai pro 3º, fecha o 3º vai pro 4º.
4. No 4º turno não existe 5º — ao fechar o 4º, trava, sem avançar.
5. **Concluir**: trava o tempo trabalhado até agora e conclui o turno.
6. **Retomar**: continua no turno atual (ex.: retomar o 4º turno).
7. **Zerar**: zera tudo e trava no zero.
8. O dia vai até meia-noite: quando bate meia-noite, independente de quantas horas trabalhadas, zera tudo para iniciar o novo dia.
9. Botões da jornada: **iniciar**, **pausar**, **café** (pausa que não conta no tempo), **retomar**, **fechar turno**, **concluir** e **zerar**.

### 5.2 Regras de contagem de tempo

1. **Pausar**: congela o contador no tempo atual.
2. **Iniciar de novo ou retomar**: continua contando de onde parou.
3. **Fechar turno**: fecha e para no tempo atual, registrando as horas daquele turno, **sem zerar o contador**.
4. Ao iniciar o segundo turno, o contador continua contando (somando ao total do dia).
5. Fechar o segundo e iniciar o terceiro: continua contando.
6. O objetivo é saber as **horas exatas trabalhadas no dia, somando todos os turnos**.

Consequências diretas destas regras (sem regra nova):
- Existe **um contador do dia, cumulativo** entre turnos; ele só volta a zero com **zerar** ou na **meia-noite**.
- Cada turno tem seu **registro de horas próprio** (duração trabalhada dentro daquele turno), gravado ao fechar o turno.
- Total do dia = soma das horas registradas dos turnos (fechados + o turno em andamento).
- Tempo de **café** não entra no tempo trabalhado.

### 5.3 Estados

| Estado | Significado | Contador do dia |
|---|---|---|
| `ZERADO` | Início do dia, após zerar ou após meia-noite. Travado no zero. | 00:00:00, parado |
| `TRABALHANDO(n)` | Turno *n* (1–4) em andamento. | contando |
| `PAUSADO(n)` | Turno *n* pausado. | congelado |
| `CAFE(n)` | Turno *n* em pausa de café. | congelado (café não conta) |
| `TURNO_FECHADO(n→n+1)` | Turno *n* fechado e registrado; o próximo turno (*n+1*) já é o turno atual, aguardando contagem (ver questão Q1). | parado no total atual |
| `TRAVADO_4` | 4º turno fechado; não há 5º. | parado no total atual |
| `CONCLUIDO(n)` | Jornada concluída no turno *n*; tempo travado. | parado no total atual |

### 5.4 Transições por botão

| Estado atual | iniciar | pausar | café | retomar | fechar turno | concluir | zerar |
|---|---|---|---|---|---|---|---|
| `ZERADO` | → `TRABALHANDO(1)` | — | — | — | — | — | permanece `ZERADO` |
| `TRABALHANDO(n)` | — | → `PAUSADO(n)` | → `CAFE(n)` | — | registra horas do turno *n*; n<4 → `TURNO_FECHADO(n→n+1)`; n=4 → `TRAVADO_4` | registra e → `CONCLUIDO(n)` | → `ZERADO` |
| `PAUSADO(n)` | → `TRABALHANDO(n)` (continua de onde parou) | — | ? (Q3) | → `TRABALHANDO(n)` | idem `TRABALHANDO(n)` (Q5) | idem `TRABALHANDO(n)` | → `ZERADO` |
| `CAFE(n)` | → `TRABALHANDO(n)` | ? (Q3) | — | → `TRABALHANDO(n)` | ? (Q5) | ? (Q5) | → `ZERADO` |
| `TURNO_FECHADO(n→n+1)` | → `TRABALHANDO(n+1)` (contador continua somando) | — | — | ? (Q1) | — | ? (Q6) | → `ZERADO` |
| `TRAVADO_4` | — | — | — | ? (Q4) | — | — | → `ZERADO` |
| `CONCLUIDO(n)` | ? (Q4) | — | — | ? (Q4) | — | — | → `ZERADO` |
| **Qualquer estado à meia-noite** | → `ZERADO` (automático) | | | | | | |

"—" = botão desabilitado/sem efeito. "?" = comportamento a definir (ver 5.7).

### 5.5 Critérios de aceite

- Sem horários fixos: o 1º turno começa quando o usuário toca **iniciar**, a qualquer hora.
- **Pausar** congela o contador; **retomar**/**iniciar** continua do mesmo valor, sem perder nem somar segundos.
- **Café** congela o contador e o tempo de café **não** é somado ao tempo trabalhado (nem do turno nem do dia).
- **Fechar turno** grava as horas daquele turno, mantém o contador do dia no valor atual (não zera) e o turno atual passa a ser o seguinte (1→2, 2→3, 3→4).
- Ao iniciar o 2º, 3º ou 4º turno, o contador do dia continua a partir do total acumulado.
- Fechar o 4º turno trava a jornada; nunca aparece "5º turno".
- **Concluir** trava o tempo trabalhado no valor atual.
- **Zerar** leva contador do dia e registros de turno a zero e trava no zero até novo **iniciar**.
- À **meia-noite** (hora local do aparelho, America/Sao_Paulo), tudo é zerado automaticamente, qualquer que seja o estado ou as horas trabalhadas.
- A tela mostra: turno atual (1º–4º), contador do dia e as horas registradas de cada turno.
- Total do dia exibido = soma exata das horas de todos os turnos.
- Fechar e reabrir o app (ou o sistema matar o app) não altera o tempo nem o estado.

### 5.6 Notas técnicas

- **Persistência entre reinícios do app:** não contar tempo com `setInterval` acumulando segundos. Guardar marcos de tempo: estado, turno atual, `inicioSegmento` (timestamp de quando começou a contar), `acumuladoTurno` e lista de horas registradas por turno. O tempo exibido = acumulado + (agora − `inicioSegmento`) quando contando. Salvar a cada transição (ex.: `localStorage` da WebView e/ou `SharedPreferences` via `LunaBridge`), assim o app reaberto recalcula o valor correto.
- **Reset de meia-noite:** usar a hora local do aparelho (America/Sao_Paulo). Guardar a data (AAAA-MM-DD) da jornada; ao abrir o app, ao voltar do segundo plano e por timer agendado para 00:00, se a data mudou → `ZERADO`. Se o app estava fechado à meia-noite, o reset acontece na próxima abertura. Se o turno estiver contando na virada, o comportamento sobre o tempo antes/depois de 00:00 está em Q7.
- **Café fora do tempo trabalhado:** ao entrar em `CAFE`, encerrar o segmento contando (somar ao acumulado) e não abrir novo segmento até retomar/iniciar. Opcionalmente registrar a duração do café à parte, só para exibição.
- A bolha flutuante (`OverlayService`) e o cronômetro do topo devem ler o mesmo estado persistido.
- Não alterar lógica de negócio existente fora da jornada (finanças etc.).

### 5.7 Questões em aberto da jornada

- **Q1 — Fechar turno: avança contando ou espera "iniciar"?** A regra 5.1-3 diz que fechar "avança automaticamente para o próximo"; as regras 5.2-3/4/5 dizem que fechar "para no tempo atual" e que o contador continua "ao iniciar o segundo turno". Interpretação adotada na tabela: o turno seguinte vira o turno atual, mas o contador fica parado até tocar **iniciar** (ou **retomar**?). Confirmar.
- **Q2 — Diferença entre pausar e café:** pela regra 5.2-1 pausar também congela o contador, ou seja, nenhum dos dois soma tempo. A diferença é só o registro/rótulo (café aparece separado)? Pausa deveria contar como tempo trabalhado? Confirmar.
- **Q3 —** Dá para ir de pausa para café (e vice-versa) diretamente?
- **Q4 — Retomar depois de concluir ou de fechar o 4º:** a regra 5.1-6 cita "retomar o 4º turno". Retomar é permitido depois de **concluir** e/ou depois de **fechar o 4º** (destravando)? Iniciar também?
- **Q5 —** Pode fechar turno ou concluir estando em pausa/café? O tempo da pausa entra no turno?
- **Q6 — Concluir vs. fechar o 4º:** qual é a diferença prática? Concluir encerra a jornada do dia em qualquer turno (ex.: concluir no 2º sem fazer 3º e 4º)? Concluir registra as horas do turno atual como "fechado"? Pode concluir em `TURNO_FECHADO`?
- **Q7 — Turno ativo na meia-noite:** o tempo trabalhado antes das 00:00 se perde ao zerar (regra 5.1-8 diz "zera tudo") ou deve ser salvo num histórico do dia anterior? E o tempo depois das 00:00 começa a contar sozinho ou exige novo **iniciar**?
- **Q8 —** Deve existir histórico de dias anteriores (relacionado ao histórico de km da F6), ou só o dia atual?
- **Q9 —** Zerar precisa de confirmação para evitar toque acidental? ~~No contexto da orb~~ — **Resolvida** só para a orb (6.1.1 M8: pede "tem certeza?"). Para o botão zerar dentro do app: **continua em aberto**.

---

## 6. Orb flutuante da Luna (bolha flutuante)

Orb com a Luna, exibida por cima de outros apps, para controlar a jornada (seção 5) sem abrir o aplicativo. As regras abaixo foram passadas pelo usuário (6.1) e depois detalhadas pelas **Decisões do Marlon (6.1.1)**, que prevalecem em caso de conflito. Pontos ainda ambíguos estão em **6.6 Questões em aberto da orb**.

### 6.1 Regras

1. ~~Ao fechar o aplicativo, aparece a orb da Luna na tela, mostrando o contador da jornada.~~ **Substituída pela decisão M7:** a orb aparece ao **minimizar** o app (não ao fechar), mostrando o contador da jornada.
2. Um toque na orb: abre as opções de jornada ao redor dela — **iniciar, pausar, zerar, café, fechar turno, concluir e microfone** — com a Luna no meio e as opções ao redor para clicar. *(Detalhado por M2 — iniciar/retomar conforme o estado — e M13 — microfone grande no centro do menu.)*
3. Outro toque: as opções somem, fica só a orb da Luna. *(Reforçado por M9: o menu só fecha quando o usuário clica para fechar.)*
4. Dois toques simultâneos: abre o aplicativo inteiro. *(Esclarecido por M1: dois toques rápidos na orb.)*

**Decisão (alternância Luna/sala do microfone):** usar um botão de alternância — um ícone pequeno na orb que troca entre Luna e sala (ícone de robô para Luna, ícone de microfone para sala). O toque simples na orb continua só abrindo e fechando o menu de opções; a alternância é feita pelo botão, evitando erro de acionar a sala quando o usuário queria falar com a Luna. Detalhes em 7.1 e 7.3; posição definida em M13.

### 6.1.1 Decisões do Marlon (orb)

- **M1.** Abrir o app: dois toques rápidos na orb.
- **M2.** Retomar: o menu da orb já mostra o botão certo conforme o estado do turno (iniciar ou retomar).
- **M3.** A orb é arrastável pela tela.
- **M4.** Fechar/esconder: um xizinho na própria orb pra fechar; arrastar a orb pra fora da tela só esconde ela mas o app continua rodando; o botão de fechar encerra o app de vez.
- **M5.** Sala de voz fica dentro da opção "rádio" na rota dos parceiros; ao entrar na sala: botão pra entrar na ligação coletiva ao vivo com todos os participantes, mandar mensagem de texto, ou segurar pra mandar áudio.
- **M6.** Se o usuário entrou na ligação, continua no canal de voz mesmo com o app fechado — fala, ouve e é ouvido.
- **M7.** A orb aparece ao minimizar o app (não ao fechar).
- **M8.** Zerar pela orb pede confirmação ("tem certeza?").
- **M9.** O menu da orb só fecha quando o usuário clica pra fechar.
- **M10.** A orb mostra o turno atual e o estado (pausado ou no café).
- **M11.** Modo inicial: sempre Luna.
- **M12.** Ao reabrir o app: abre na tela inicial e volta sempre aonde o usuário parou.
- **M13.** Botão de alternância Luna/sala: fica num canto da orb, pequeno, com o ícone do modo atual (robô quando é a Luna, microfone quando é a sala); o microfone do menu é maior, fica no centro do menu e só aparece com o menu aberto; se o menu tá fechado, só existe um microfone na tela: o da orb; se tá aberto, o da orb é o pequeno do canto e o do menu é o grande do centro.

**Observações sobre as decisões (sem regra nova):**
- **M4 × M6:** M6 exige manter a ligação de voz com o app fechado, o que só é possível com **foreground service nativo do tipo `microphone`** (6.5). Isso conflita com M4 ("o botão de fechar encerra o app de vez") — ver Q12.
- **M12:** "abre na tela inicial" e "volta sempre aonde o usuário parou" podem ser contraditórios — ver Q13.
- **M13 × regra 6.1-2:** a regra original põe "a Luna no meio" do menu; M13 põe o microfone grande "no centro do menu" — ver Q14.

### 6.2 Estados da orb

| Estado | O que aparece |
|---|---|
| `OCULTA` | App aberto em primeiro plano; a orb não aparece. |
| `RECOLHIDA` | Só a orb da Luna, mostrando o contador da jornada (mesmo valor do cronômetro do topo), o **turno atual (1º–4º)** e o **estado** (trabalhando, pausado ou no café) (M10). Botão de alternância pequeno num canto (M13) e o xizinho de fechar (M4). É o único microfone na tela (M13). |
| `MENU_ABERTO` | Opções de jornada ao redor: **iniciar ou retomar** (conforme o estado — M2), pausar, zerar, café, fechar turno, concluir; **microfone grande no centro do menu** (M13). O botão de alternância continua pequeno no canto da orb. |
| `ESCONDIDA` | Orb arrastada pra fora da tela: não aparece, mas o app continua rodando (M4). Como trazê-la de volta: Q15. |
| `CONFIRMAR_ZERAR` | Diálogo "tem certeza?" depois de tocar em zerar na orb (M8). |

**Modo do microfone** (em `RECOLHIDA` e `MENU_ABERTO`): `MODO_LUNA` (ícone de robô) ou `MODO_SALA` (ícone de microfone), trocado só pelo botão de alternância. **Sempre começa em `MODO_LUNA`** (M11).

**Ligação (independente da orb):** `NA_LIGACAO` quando o usuário entrou na ligação coletiva da sala (M5); continua mesmo com o app fechado (M6).

### 6.3 Interações

| Estado atual | Ação | Resultado |
|---|---|---|
| App aberto | **Minimizar** o app (home/trocar de app) | → `RECOLHIDA` (M7) |
| App aberto | Fechar o app | A orb **não** aparece (M7); ligação ativa: ver M6/Q12 |
| `RECOLHIDA` | Um toque na orb | → `MENU_ABERTO` |
| `MENU_ABERTO` | Toque para fechar (na orb/Luna) | → `RECOLHIDA`. O menu **só** fecha assim (M9) |
| `MENU_ABERTO` | Toque em iniciar/retomar, pausar, café, fechar turno, concluir | Executa a ação da jornada (tabela 5.4); o menu **continua aberto** (M9); orb atualiza contador, turno e estado (M10) |
| `MENU_ABERTO` | Toque em zerar | → `CONFIRMAR_ZERAR` ("tem certeza?") (M8) |
| `CONFIRMAR_ZERAR` | Confirmar / Cancelar | Confirmar: zera a jornada (tabela 5.4) e volta ao menu. Cancelar: volta ao menu sem mudar nada |
| `MENU_ABERTO` | Toque no microfone grande do centro | Voz em tempo real (seção 7, uso 1) com o modo atual: `MODO_LUNA` → Luna; `MODO_SALA` → sala de voz |
| `RECOLHIDA` | Toque no microfone/ícone do canto (único microfone na tela — M13) | Comportamento exato a definir — Q16 |
| `RECOLHIDA` ou `MENU_ABERTO` | Toque no botão de alternância | `MODO_LUNA` ⇄ `MODO_SALA`. **Não** abre nem fecha o menu |
| `RECOLHIDA` ou `MENU_ABERTO` | **Dois toques rápidos** na orb | Abre o app inteiro → `OCULTA` (M1); o app abre conforme M12 (Q13) |
| `RECOLHIDA` ou `MENU_ABERTO` | Arrastar a orb | Move a orb pela tela (M3) |
| `RECOLHIDA` ou `MENU_ABERTO` | Arrastar a orb pra fora da tela | → `ESCONDIDA`; app continua rodando (M4) |
| `RECOLHIDA` ou `MENU_ABERTO` | Toque no xizinho da orb | Fecha a orb (M4) — se também encerra o app/ligação: Q12 |
| Qualquer | Botão de fechar (encerrar o app) | Encerra o app de vez (M4) — conflito com M6: Q12 |

### 6.4 Critérios de aceite

- Ao **minimizar** o app (com a permissão concedida), a orb aparece em até ~1 s e mostra o contador da jornada atualizado em tempo real (M7). Ao fechar o app, ela não aparece.
- A orb mostra o turno atual (1º–4º) e o estado (pausado ou no café) (M10).
- O contador da orb é sempre igual ao do cronômetro do app (mesma fonte de estado — seção 5).
- Um toque abre o menu radial; o menu só fecha quando o usuário toca para fechar — tocar numa ação de jornada não fecha o menu (M9).
- O menu mostra **iniciar** ou **retomar** conforme o estado do turno, nunca os dois errados para o estado (M2).
- Cada botão de jornada na orb tem o mesmo efeito do botão correspondente dentro do app (tabela 5.4), incluindo persistência e reset de meia-noite.
- **Zerar pela orb sempre pede "tem certeza?"**; cancelar não altera nada (M8).
- **Dois toques rápidos** abrem o aplicativo inteiro (M1), sem abrir o menu por engano.
- A orb pode ser arrastada para qualquer lugar da tela (M3); arrastada pra fora, some e o app continua rodando (M4).
- A orb tem um xizinho para fechar (M4).
- Os botões ficam legíveis e clicáveis no celular e no tablet, sem sair da borda da tela.
- Visual com a paleta da F8 e a foto nova da Luna (F9).
- Sem a permissão "Exibir sobre outros apps", o app explica e leva o usuário à tela de configuração, sem travar.
- O botão de alternância Luna/sala fica visível, pequeno, num canto da orb (M13).
- O modo atual está sempre indicado pelo ícone (robô = Luna, microfone = sala).
- O modo começa sempre em Luna (M11).
- Tocar no botão de alternância nunca abre nem fecha o menu.
- O toque simples na orb nunca muda o modo.
- Com o menu fechado existe só um microfone na tela (o da orb); com o menu aberto, o da orb é o pequeno do canto e o do menu é o grande do centro, que só aparece com o menu aberto (M13).
- Se o usuário entrou na ligação da sala, continua falando, ouvindo e sendo ouvido com o app fechado (M6).
- Ao reabrir o app, ele abre conforme M12 (comportamento exato: Q13).

### 6.5 Notas técnicas

- **Já existe uma bolha flutuante desde a v6.0.0:** `OverlayService` (foreground service `specialUse`, subtipo "Bolha flutuante de controle de turno do motorista"), aberto/fechado pela WebView via `LunaBridge.showOverlay()` / `hideOverlay()`; no 6.2.1 há o botão "Mostrar bolha" nos ajustes. A orb deve evoluir esse serviço, não criar outro.
- **Aparecer ao minimizar (M7):** mostrar a orb em `onStop`/`onPause` da `MainActivity` (ou `ProcessLifecycleOwner` → `ON_STOP`) e escondê-la ao voltar ao primeiro plano; não mostrar quando a activity está sendo finalizada (`isFinishing`) por fechamento do app.
- **Permissão `SYSTEM_ALERT_WINDOW` ("Exibir sobre outros apps")** já está no manifesto; precisa ser concedida pelo usuário em Configurações (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`); checar com `Settings.canDrawOverlays()`.
- **Foreground service** com notificação persistente para a orb não ser encerrada pelo sistema (`FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_SPECIAL_USE` já declarados; `POST_NOTIFICATIONS` precisa ser pedida no Android 13+). Se o mesmo serviço também mantiver localização (seção 4.1), combinar com o tipo `location` ou separar serviços.
- **Ligação com app fechado (M6):** exige mover o áudio da sala para o lado **nativo** (ex.: SDK Android do LiveKit ou WebRTC nativo) rodando num **foreground service do tipo `microphone`** (`FOREGROUND_SERVICE_MICROPHONE`, Android 14), iniciado enquanto o app ainda está visível (ao entrar na ligação). A WebView atual não mantém áudio com o app fechado. O serviço precisa sobreviver ao fechamento da activity — e isso conflita com "encerrar o app de vez" (M4/Q12).
- **Layout radial (M13):** microfone grande no centro do menu e as opções de jornada distribuídas em círculo ao redor (posição da Luna: Q14); perto de uma borda, usar arco/semicírculo para os botões não saírem da tela. Botão de alternância pequeno fixo num canto da orb. Janela `TYPE_APPLICATION_OVERLAY`.
- **Detecção de toques (M1):** toque simples × **duplo toque** com `GestureDetector` (`onSingleTapConfirmed` para abrir/fechar o menu e `onDoubleTap` para abrir o app); o toque simples espera a janela do duplo toque (~300 ms). Arrasto (M3) com `onScroll`/`ACTION_MOVE` atualizando `WindowManager.LayoutParams`; soltar fora da área visível → `ESCONDIDA` (M4). Toques no botão de alternância e no xizinho são tratados à parte e não disparam o gesto da orb.
- **Mesmo estado da jornada da seção 5:** a orb (nativa) e a WebView leem e gravam o mesmo estado persistido (ex.: `SharedPreferences` exposto à WebView pelo `LunaBridge`), com timestamps — a orb calcula contador, turno e estado (M10) sozinha, sem depender da WebView ativa. O botão iniciar/retomar (M2) é escolhido a partir desse estado.
- **Modo do microfone (M11):** sempre inicia em `MODO_LUNA`; não é necessário persistir o último modo.
- **Reabrir o app (M12):** salvar a última tela/aba e o scroll da WebView (ex.: `localStorage`) para restaurar ao reabrir — conforme a resposta da Q13.
- **Microfone:** voz em tempo real com a Luna ou com a sala de voz/walkie-talkie (F2/F3) — detalhado na seção 7 (uso 1). Sala dentro da opção "rádio" da Rota dos Parceiros (M5).

### 6.6 Questões em aberto da orb

- **Q1 —** ~~"Dois toques simultâneos": é tocar com dois dedos ao mesmo tempo ou toque duplo?~~ **Resolvida** (M1): dois toques rápidos na orb (duplo toque).
- **Q2 — Botões por estado:** ~~retomar não está na lista da orb. Como retomar após pausa/café pela orb?~~ **Resolvida** (M2): o menu mostra iniciar ou retomar conforme o estado do turno. Continua em aberto: os outros botões que não se aplicam ao estado (ex.: pausar com a jornada zerada) somem, ficam apagados ou continuam iguais?
- **Q3 — Mover e dispensar a orb:** ~~pode arrastar? Como fechar/esconder?~~ **Resolvida** (M3, M4): é arrastável; xizinho fecha; arrastar pra fora esconde e o app continua rodando. Continua em aberto: a orb gruda na borda ao soltar?
- **Q4 — Microfone:** ~~o que faz exatamente?~~ **Resolvido na seção 7:** voz em tempo real com a Luna ou com a sala de voz (uso 1). ~~Como alternar entre Luna e sala~~ — **Resolvida:** botão de alternância na orb (ver 6.1, M13 e 7.5 Q1). ~~Se dá para ouvir os outros~~ — **Resolvida** (M6): na ligação, o usuário fala, ouve e é ouvido, mesmo com o app fechado. Continua em aberto: no modo sala, o microfone da orb entra na ligação coletiva ao vivo ou funciona como "segurar pra mandar áudio" (M5 tem as duas opções) — ver Q16.
- **Q5 — Quando a orb aparece:** ~~só ao fechar ou também ao minimizar?~~ **Resolvida** (M7): ao minimizar, não ao fechar. Continua em aberto: aparece também com a jornada zerada ou só com jornada em andamento?
- **Q6 —** ~~Zerar pela orb precisa de confirmação?~~ **Resolvida** (M8): sim, "tem certeza?".
- **Q7 —** ~~Depois de tocar num botão de jornada, o menu fecha sozinho?~~ **Resolvida** (M9): não; só fecha quando o usuário clica para fechar.
- **Q8 —** ~~A orb mostra o turno atual e o estado?~~ **Resolvida** (M10): sim, turno atual e estado (pausado ou no café).
- **Q9 —** ~~Modo padrão do microfone: Luna ou sala?~~ **Resolvida** (M11): sempre Luna.
- **Q10 —** ~~Modo salvo entre sessões?~~ **Resolvida** (M11): não; sempre começa em Luna.
- **Q11 —** ~~Posição do botão de alternância e como não confundir com o microfone do menu?~~ **Resolvida** (M13): pequeno num canto da orb; o microfone do menu é grande, no centro, e só aparece com o menu aberto.
- **Q12 — Fechar × continuar na ligação (M4 × M6):** o xizinho da orb fecha só a orb ou o app inteiro? Ao encerrar o app pelo botão de fechar, a ligação também cai, ou a ligação continua (M6) e só sai pelo botão "sair da ligação"? Se a ligação continua, como o usuário vê que está na ligação (notificação persistente)?
- **Q13 — Reabrir o app (M12):** "abre na tela inicial" e "volta sempre aonde o usuário parou" — qual vale? Ex.: volta à última tela se só minimizou e abre na tela inicial se o app foi fechado?
- **Q14 — Centro do menu (M13 × 6.1-2):** o microfone grande fica no centro **no lugar** da Luna, ou sobre/abaixo da imagem da Luna?
- **Q15 — Orb escondida (M4):** como trazer de volta uma orb arrastada pra fora da tela (notificação, abrir o app, puxar da borda)?
- **Q16 — Microfone com o menu fechado (M13):** o ícone pequeno do canto serve para alternar o modo **e** para falar, ou só alterna? Com o menu fechado e no modo Luna o ícone é um robô — então o "único microfone na tela" é qual?

---

## 7. Separação dos três usos de voz/IA

O app tem **três usos diferentes de voz**, que não devem ser misturados: conversa em tempo real (orb), comandos que viram ação (card da página inicial) e ditado de mensagens (chat). As regras abaixo foram passadas pelo usuário; pontos ambíguos estão em **7.5 Questões em aberto de voz/IA**.

### 7.1 Regras

1. **Microfone da orb flutuante:** voz em tempo real — falar com a Luna ou com a sala de voz (Rota dos Parceiros).
2. **Card na página inicial:** comandos de voz que viram ação — ex.: "Luna, anota pra mim tal valor", "Luna, acabei de fazer um valor em tal aplicativo" (anotar corrida particular, dar pausa, anotar valor específico).
3. **Chat da Rota dos Parceiros:** aceita voz para ditar mensagens em vez de digitar.

**Decisão (uso 1 — alternância Luna/sala):** Um botão de alternância — um ícone pequeno na orb que troca entre Luna e sala (ícone de robô para Luna, ícone de microfone para sala). O toque simples na orb continua só abrindo e fechando o menu de opções; a alternância é feita pelo botão, evitando erro de acionar a sala quando o usuário queria falar com a Luna.

### 7.2 Comparação

| Uso | Onde | Entrada | Saída | Tecnologia sugerida | Latência esperada |
|---|---|---|---|---|---|
| **1. Voz em tempo real** | Botão microfone da orb flutuante (seção 6) | Fala contínua do usuário (áudio ao vivo) | Com a Luna: resposta falada da Luna. Com a sala: áudio para todos da sala de Valadares (F2/F3) | Luna: Gemini Live API (áudio em tempo real) ou agente LiveKit com Gemini. Sala: WebRTC atual → LiveKit (SFU) | Tempo real (conversa; idealmente < 1 s) |
| **2. Comando de voz → ação** | Card na página inicial | Frase curta falada ("Luna, anota…") | Ação no app: anotar valor/corrida particular, pausar jornada etc., com confirmação | Reconhecimento de fala (pt-BR) → texto → Gemini interpreta a intenção e extrai os dados → ação em jornada/finanças | Alguns segundos são aceitáveis |
| **3. Ditado** | Campo de mensagem do chat da Rota dos Parceiros (F2) | Fala do usuário | Texto no campo de mensagem, para revisar e enviar | Android `SpeechRecognizer` (via `LunaBridge`) ou Web Speech API, idioma pt-BR | Quase imediato (texto aparece enquanto fala) |

### 7.3 Critérios de aceite

**Uso 1 — orb (tempo real)**
- Tocar no microfone da orb inicia uma conversa de voz ao vivo com o app minimizado (a orb aparece ao minimizar — M7).
- Na sala de voz (dentro da opção "rádio" da Rota dos Parceiros — M5), o usuário pode entrar na ligação coletiva ao vivo, mandar mensagem de texto ou segurar para mandar áudio; quem está na ligação continua falando, ouvindo e sendo ouvido mesmo com o app fechado (M6).
- O modo do microfone começa sempre em Luna (M11).
- O usuário consegue falar com a **Luna** e ouvir a resposta falada, **ou** falar na **sala de voz de Valadares**, e fica claro na orb com qual dos dois está falando.
- A escolha entre Luna e sala é feita só pelo botão de alternância da orb (ícone de robô = Luna, ícone de microfone = sala); o microfone sempre fala com o modo indicado no ícone (critérios completos em 6.4).
- Na sala, o comportamento segue F2 (walkie-talkie, todos ouvem).

**Uso 2 — card da página inicial (comando → ação)**
- Frases como "Luna, anota pra mim tal valor" e "Luna, acabei de fazer um valor em tal aplicativo" viram a ação correspondente (anotar corrida particular, dar pausa, anotar valor específico), com valor e aplicativo extraídos da fala.
- Antes de executar, o app mostra o que entendeu (ex.: "Anotar R$ 25,00 — corrida particular") — ver Q2 sobre confirmação.
- "Dar pausa" executa a mesma ação do botão **pausar** da jornada (seção 5); valores entram nas finanças existentes, sem mudar a lógica de negócio.
- Se a frase não for entendida, o app avisa e não faz nada.

**Uso 3 — chat (ditado)**
- O chat tem botão de microfone que transcreve a fala em pt-BR para o campo de texto.
- O texto ditado pode ser editado antes de enviar; nada é enviado automaticamente.
- Ditado é diferente da mensagem de áudio gravada da F2 (aqui sai texto, não áudio).

### 7.4 Notas técnicas

- **Orb / tempo real (uso 1):**
  - Luna: Gemini Live API (streaming de áudio bidirecional) ou um agente LiveKit com Gemini na sala. A chave do Gemini não deve ficar no APK (F10) — usar token efêmero/servidor.
  - Sala: WebRTC/MQTT atual (seção 4.2) no MVP; LiveKit (SFU) quando houver servidor de tokens.
  - Com o app fechado, o áudio precisa rodar no lado nativo e em foreground service do tipo `microphone` (seção 6.5).
- **Card / comando → ação (uso 2):**
  - Fala → texto com `SpeechRecognizer` (pt-BR) ou STT do próprio Gemini; texto → Gemini com saída estruturada (JSON, ex.: `{acao: "anotar_valor", valor: 25.00, app: "…", tipo: "particular"}`) ou function calling.
  - O app só executa ações de uma **lista fechada** (ver Q4) e sempre via as mesmas funções dos botões existentes (jornada/finanças).
  - Valores em reais: tratar fala como "vinte e cinco e cinquenta" → 25,50.
- **Chat / ditado (uso 3):**
  - Android `SpeechRecognizer` com `EXTRA_LANGUAGE = "pt-BR"` exposto pelo `LunaBridge`, ou Web Speech API na WebView (o suporte na WebView Android é limitado — preferir o nativo).
  - Ditado não precisa de Gemini.
- Permissão `RECORD_AUDIO` já existe; pedir em tempo de execução se ainda não concedida.

### 7.5 Questões em aberto de voz/IA

- **Q1 — Alternar Luna × sala no microfone da orb:** ~~como o usuário escolhe falar com a Luna ou com a sala? Dois botões, toque curto × segurar, palavra de ativação ("Luna, …"), ou um seletor?~~ **Resolvida:** botão de alternância na orb (robô = Luna, microfone = sala); o toque simples só abre/fecha o menu. Ver 6.1, 6.3, 6.4 e 7.1. Dúvidas relacionadas (modo padrão, modo salvo, posição do botão — 6.6 Q9–Q11) também **resolvidas** pelas decisões M11 e M13. Ainda em aberto: o que o microfone faz no modo sala (6.6 Q4/Q16).
- **Q2 — Confirmação:** confirmar sempre antes de salvar valores/executar ações do card, ou só quando houver dúvida?
- **Q3 — Offline:** o que deve funcionar sem internet (ex.: ditado e comandos simples com reconhecimento offline do Android)? Gemini e a sala exigem internet.
- **Q4 — Lista de comandos suportados:** quais comandos exatamente? Exemplos recebidos: anotar corrida particular, dar pausa, anotar valor específico, "acabei de fazer um valor em tal aplicativo". Também iniciar/fechar turno/café/concluir/zerar? Quais aplicativos de corrida devem ser reconhecidos?
- **Q5 —** O card da página inicial escuta só quando o usuário toca no microfone, ou fica ouvindo a palavra "Luna" o tempo todo?
- **Q6 —** A Luna na orb (uso 1) também pode executar ações (como no uso 2), ou só conversa?

---

## 8. Questões em aberto
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
11. Lógica da jornada: ver as questões Q1–Q9 na seção 5.7.
12. Orb flutuante da Luna: ver a seção 6.6 (Q1 e Q6–Q11 resolvidas pelas decisões do Marlon; Q2–Q5 parcialmente; Q12–Q16 abertas).
13. Voz/IA: ver as questões Q1–Q6 na seção 7.5.
