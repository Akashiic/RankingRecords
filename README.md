# TempoRanking

Plugin para **Crucible 1.7.10** com:

- tempo da **sessão atual** de cada jogador (quanto tempo está logado agora);
- **tempo total jogado** e **ranking top 1–9** de quem mais jogou (meses, dias, horas, minutos e segundos);
- **ranking top 1–9 dos mais ricos** (EssentialsX + Vault);
- tudo em **placeholders para o Holographic Displays 2.2.6**, inclusive os do próprio jogador
  ("seu tempo", "seu saldo", "sua posição"), que mostram um valor diferente para cada jogador;
- de bônus, os mesmos placeholders no **PlaceholderAPI** (TAB, scoreboard etc.).

Testado num Crucible `1.7.10-staging-67b53034` (o mesmo `server.jar` do seu servidor) com
Holographic Displays 2.2.6, ProtocolLib 3.7.0, EssentialsX 2.15.0.evernife, Vault 1.7.3,
PlaceholderAPI 2.11.6, LuckPerms Legacy 5.5.77 e Java 21 com o seu `java9args.txt`.

## Atualizando da 1.0.0

O `config.yml` antigo não ganha as opções novas sozinho. Copie as seções `ignorar:`,
`hologramas.papi-placeholders` e `linhas.ignorado-sim/nao` do `config.yml` que vem dentro do jar,
ou apague o seu `config.yml` para ele ser recriado. Sem isso, valem os padrões: OPs ignorados,
nenhum grupo e `%vault_groups%` como placeholder de grupo.

## Instalação

1. Coloque `TempoRanking-1.2.0.jar` em `plugins/` (apague o 1.0.0 se tiver).
2. Reinicie o servidor (evite `/reload`).
3. Na primeira inicialização ele importa o tempo jogado das estatísticas vanilla
   (`world/stats/*.json`), assim o ranking já começa com o histórico do servidor.
   Os nomes vêm do `usercache.json` e do userdata do Essentials.

Dependências (todas opcionais, mas já estão no seu servidor):
Holographic Displays, ProtocolLib (necessário para os placeholders "do próprio jogador" nos hologramas),
Essentials + Vault (ranking de dinheiro e AFK), PlaceholderAPI.

## Placeholders

No Holographic Displays use `{chave}`. No PlaceholderAPI use `%temporanking_chave%`.
`N` vai de 1 até `tamanho-top` (padrão 9).

| Chave | O que mostra |
|---|---|
| `tempo_top_N` | linha pronta: `Fulano - 2 meses 3d 4h 5m 6s` (formato em `linhas.tempo`) |
| `tempo_top_N_nome` | nome do top N em tempo |
| `tempo_top_N_tempo` | tempo total do top N |
| `rico_top_N` | linha pronta: `Fulano - $150.781.938,12` (formato em `linhas.rico`) |
| `rico_top_N_nome` | nome do top N em dinheiro |
| `rico_top_N_saldo` | saldo do top N |
| `tempo_meu_total` | **seu** tempo total jogado |
| `tempo_minha_sessao` | **sua** sessão atual (desde que entrou) |
| `tempo_minha_posicao` | **sua** posição no ranking de tempo (se você é ignorado, mostra a posição que teria) |
| `rico_meu_saldo` | **seu** saldo |
| `rico_minha_posicao` | **sua** posição no ranking de dinheiro |
| `meu_nome` | seu nome |
| `tempo_ignorado` / `rico_ignorado` | `sim`/`nao`: se você está fora do ranking (texto em `linhas.ignorado-sim/nao`) |

Os seis últimos mudam de jogador para jogador. O HD 2.2.6 só tem placeholders globais,
então o plugin troca o texto no pacote que vai para cada jogador, via ProtocolLib.

## PlaceholderAPI dentro dos hologramas

Escreva `{papi:identificador}` (o placeholder do PAPI **sem** os `%`) numa linha do HD.
Cada jogador vê o próprio valor:

```
/hd addline meuholo &7Saldo: &a{papi:vault_eco_balance_formatted}
/hd addline meuholo &7Nível: &e{papi:player_level}
```

O plugin acha sozinho os `{papi:...}` que já estão no `database.yml` do HD e os que você
escreve nos comandos `/hd`. Se algum não atualizar (por exemplo, se você editou o
`database.yml` na mão com o servidor ligado), coloque o nome em
`hologramas.papi-placeholders` e dê `/temporanking reload` e depois `/hd reload`.
No seu servidor já estão instaladas as expansões `vault`, `player` e `essentials`.

## Tirar jogadores dos rankings (OPs, permissão e grupos)

Na seção `ignorar` da config:

- `ops: true`: quem é OP (`ops.json`) não aparece nos rankings.
- **Permissão**: dê para o grupo no LuckPerms.
  - `temporanking.ignorar`: fora dos dois rankings.
  - `temporanking.ignorar.tempo`: fora só do ranking de tempo.
  - `temporanking.ignorar.ricos`: fora só do ranking de ricos.

  ```
  /lp group admin permission set temporanking.ignorar true
  ```
  Essas permissões vêm com `default: false`, então OP **não** as ganha automaticamente
  (para OP use `ops: true`). Cuidado: quem tem `*` no LuckPerms também as recebe.
- `grupos: [admin, moderador]`: grupos ignorados. O grupo do jogador é lido:
  - pelo **Vault** (`grupo-via-vault: true`), com todos os grupos do LuckPerms, inclusive herdados; e/ou
  - por um **placeholder do PAPI** (`grupo-via-placeholder`), por exemplo `%vault_groups%`
    (padrão, todos os grupos), `%vault_rank%` (grupo principal) ou `%luckperms_primary_group_name%`.

Jogadores online são reavaliados a cada atualização do ranking. Para os **offline**, permissão e grupo
são consultados em segundo plano (o seu LuckPerms usa MySQL e recusa esse tipo de consulta na thread
principal). O resultado fica em `plugins/TempoRanking/ignorados.yml` e é reconsultado a cada
`verificar-offline-minutos` (padrão 30). Para forçar na hora, use `/temporanking verificar`.
Para ver quem está fora e por quê, use `/temporanking ignorados`.

## API para Skript (skript-reflect) e outros plugins

Testada com o Skript 2.6.3-crucible e o skript-reflect 2.3-crucible
([Akashiic/Skript](https://github.com/Akashiic/Skript), [Akashiic/skript-reflect](https://github.com/Akashiic/skript-reflect)).
O arquivo [`skript/temporanking-exemplos.sk`](skript/temporanking-exemplos.sk) tem um script pronto com
`/meutempo`, `/toptempo`, um anúncio de novo TOP 1 e um prêmio a cada 10 horas jogadas.

```
import:
	com.temporanking.api.TempoRankingAPI
	com.temporanking.api.TopPositionChangeEvent
	com.temporanking.api.RankingUpdateEvent

command /meutempo:
	trigger:
		send "Total: %TempoRankingAPI.getFormattedTotal(player)% (##%TempoRankingAPI.getTimePosition(player)%)"

on TopPositionChangeEvent:
	event.getPosition() is 1
	broadcast "%event.getNewName()% agora e TOP 1 em %event.getType()%!"
```

No Skript, `#` começa um comentário: para escrever `#` dentro de um texto, use `##`.

### Métodos de `TempoRankingAPI` (todos estáticos)

`player` pode ser um jogador online ou offline. Posições começam em 1 e `-1` quer dizer "sem dados".

| Método | Retorna |
|---|---|
| `getTotalMillis(player)` / `getTotalSeconds(player)` | tempo total jogado (inclui a sessão atual) |
| `getFormattedTotal(player)` | tempo total formatado (`2 meses 3d 4h 5m 6s`) |
| `getSessionMillis(player)` / `getSessionSeconds(player)` | sessão atual (`-1` se offline) |
| `getFormattedSession(player)` | sessão atual formatada |
| `getTimePosition(player)` | posição no ranking de tempo (para ignorados, a posição que teria) |
| `setTotalMillis(player, ms)` | define o tempo total |
| `addTotalMillis(player, ms)` | soma tempo (negativo subtrai) |
| `getTopTimeName(pos)` / `getTopTimeUuid(pos)` | quem está na posição do top de tempo (`null` se vazia) |
| `getTopTimeMillis(pos)` / `getTopTimeFormatted(pos)` | tempo de quem está na posição |
| `getTimeRankingSize()` | quantos jogadores estão no ranking de tempo |
| `getBalance(player)` / `getFormattedBalance(player)` | saldo (`-1`/`null` se desconhecido) |
| `getMoneyPosition(player)` | posição no ranking de ricos |
| `getTopMoneyName(pos)` / `getTopMoneyUuid(pos)` | quem está na posição do top de ricos |
| `getTopMoneyBalance(pos)` / `getTopMoneyFormatted(pos)` | saldo de quem está na posição |
| `getMoneyRankingSize()` | quantos jogadores estão no ranking de ricos |
| `isIgnoredTime(player)` / `isIgnoredMoney(player)` | se o jogador está fora do ranking |
| `formatTime(ms)` / `formatSeconds(s)` / `formatMoney(valor)` | formata como na config |
| `placeholder("chave", player)` | resolve qualquer placeholder do plugin (ex.: `"tempo_top_1"`) |
| `getTopSize()` | tamanho do top (`tamanho-top`) |
| `refresh()` | recalcula os dois rankings agora |
| `toPlayer("uuid")` | converte um UUID de texto em jogador |

### Eventos

| Evento | Quando | Métodos |
|---|---|---|
| `TopPositionChangeEvent` | uma posição do top mudou de dono | `getType()` (`"tempo"`/`"ricos"`), `isTempo()`, `isRicos()`, `getPosition()`, `getOldName()`, `getNewName()`, `getOldUuid()`, `getNewUuid()` |
| `RankingUpdateEvent` | um ranking foi recalculado (tempo a cada 5 s, ricos a cada 60 s) | `getType()`, `getSize()` |

O `TopPositionChangeEvent` não dispara na primeira montagem do ranking, quando o servidor liga.
Quando alguém entra ou sai do meio do top, todas as posições abaixo mudam e cada uma dispara um evento.

## Exemplos de hologramas

Top 9 de tempo:

```
/hd create toptempo &6&lTOP 9 - MAIS TEMPO ONLINE
/hd addline toptempo {tempo_top_1}
/hd addline toptempo {tempo_top_2}
/hd addline toptempo {tempo_top_3}
/hd addline toptempo {tempo_top_4}
/hd addline toptempo {tempo_top_5}
/hd addline toptempo {tempo_top_6}
/hd addline toptempo {tempo_top_7}
/hd addline toptempo {tempo_top_8}
/hd addline toptempo {tempo_top_9}
/hd addline toptempo &7Seu tempo: &a{tempo_meu_total} &7(#{tempo_minha_posicao})
/hd addline toptempo &7Sessão atual: &e{tempo_minha_sessao}
```

Top 9 de mais ricos:

```
/hd create topricos &6&lTOP 9 - MAIS RICOS
/hd addline topricos {rico_top_1}
/hd addline topricos {rico_top_2}
/hd addline topricos {rico_top_3}
/hd addline topricos {rico_top_4}
/hd addline topricos {rico_top_5}
/hd addline topricos {rico_top_6}
/hd addline topricos {rico_top_7}
/hd addline topricos {rico_top_8}
/hd addline topricos {rico_top_9}
/hd addline topricos &7Seu saldo: &a{rico_meu_saldo} &7(#{rico_minha_posicao})
```

Montando a linha do seu jeito, com nome e valor separados:

```
/hd addline toptempo &e1º &f{tempo_top_1_nome} &8» &b{tempo_top_1_tempo}
```

## Comandos

| Comando | Permissão | Descrição |
|---|---|---|
| `/tempo` | `temporanking.tempo` (todos) | sua sessão, seu tempo total e sua posição |
| `/tempo <jogador>` | `temporanking.tempo.outros` (todos) | o mesmo para outro jogador (online ou offline) |
| `/tempo top` | `temporanking.tempo` | top de tempo no chat |
| `/ricos` | `temporanking.ricos` (todos) | top de dinheiro no chat |
| `/temporanking reload` | `temporanking.admin` (OP) | recarrega a config |
| `/temporanking atualizar` | admin | recalcula os dois rankings agora |
| `/temporanking salvar` | admin | salva `dados.yml` |
| `/temporanking importar` | admin | reimporta as estatísticas vanilla (usa o maior valor, não duplica) |
| `/temporanking settempo <jogador> <tempo>` | admin | define o tempo, ex.: `1me 2d 3h 4m 5s` |
| `/temporanking placeholders` | admin | lista os placeholders (e os `{papi:...}` ativos) |
| `/temporanking ignorados` | admin | quem está fora dos rankings e o motivo (op, permissão, grupo) |
| `/temporanking verificar` | admin | reconsulta agora a permissão e o grupo dos jogadores offline |

Aliases: `/playtime`, `/tempoonline`, `/toprico`, `/topricos`, `/tr`.

## Como funciona

- **Tempo**: a cada segundo soma o tempo de cada jogador online. Salva em
  `plugins/TempoRanking/dados.yml` a cada 5 minutos e ao desligar. Com
  `contar-tempo-afk: false`, o tempo em `/afk` do Essentials não conta.
- **Ricos**: o Vault não tem função de "top saldos", então o plugin lê
  `plugins/Essentials/userdata/*.yml` numa thread assíncrona a cada 60 s (só `lastAccountName`,
  `money` e `npc`) e usa o saldo ao vivo (Vault) de quem está online. Contas NPC do Essentials
  (`npc: true`, 192 no seu servidor) ficam de fora por padrão. Para tirar staff ou contas do
  servidor, use `ricos.ignorar-jogadores`.
- **Formato do tempo**: em `tempo.formato` dá para trocar os sufixos (ex.: ` dias`, ` horas`),
  esconder as unidades zeradas e limitar quantas unidades aparecem (`maximo-unidades: 3` →
  `2 meses 3d 4h`). Um mês conta como 30 dias (`dias-por-mes`).
- **Formato do dinheiro**: `ricos.formato` (padrão `$150.781.938,12`; com `abreviar: true` fica `$150,78M`).

## Compilar

Copie para `libs/` os jars do seu servidor: `server.jar`, `HolographicDisplays.jar`,
`ProtocolLib.jar`, `PlaceholderAPI-2.11.6.jar`, `Vault.jar` e `EssentialsX-2.15.0.evernife.v1b.jar`.
Depois rode:

```bash
mvn package
```

O jar fica em `target/TempoRanking-1.2.0.jar` (bytecode Java 8, que o remapper de plugins do Crucible aceita).
