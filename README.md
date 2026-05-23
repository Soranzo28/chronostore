# ChronoStore

Plugin Paper para monitoramento e limitação de tempo online diário por jogador.

## Requisitos

- Paper 26.1.2 ou superior
- Java 21+

## Instalação

```
mvn package
cp target/chronostore-1.0-SNAPSHOT.jar /seu-servidor/plugins/
```

O banco de dados SQLite é criado automaticamente em `plugins/ChronoStore/data.db` na primeira inicialização.

## Funcionamento

Jogadores cadastrados no sistema acumulam tempo jogado a cada sessão. Ao atingir o limite diário, o jogador é kickado e impedido de entrar até o reset. O tempo é resetado automaticamente à meia-noite. Nos fins de semana o monitoramento é suspenso automaticamente.

**Sessão**: iniciada no join, encerrada no quit ou kick. O tempo é persistido no banco ao final de cada sessão. Em caso de crash do servidor, o tempo acumulado até o momento do desligamento é salvo no `onDisable`.

**tbsp** (colher de cha): atraso configuravel entre o join e o inicio da contagem, pensado para jogadores com maquinas mais lentas que precisam de tempo para carregar o mundo antes de comecar a ser monitorados. Durante o tbsp, o jogador ve uma contagem regressiva na action bar e no tab list.

**Pausa manual**: suspende kicks e avisos para todos os jogadores. O tempo congelado no momento da pausa é mantido no tab list. Ao retomar, as sessões são recalculadas descartando o tempo em que o servidor estava pausado.

## Comandos

Todos os comandos exigem OP ou a permissão `chronostore.admin`.

| Comando | Descricao |
|---|---|
| `/chrono add <player>` | Inicia o monitoramento do jogador |
| `/chrono remove <player>` | Encerra o monitoramento do jogador |
| `/chrono status <player>` | Exibe tempo jogado, limite, restante e tbsp |
| `/chrono limit <player> <minutos>` | Define o limite diario em minutos |
| `/chrono tbsp <player> <segundos>` | Define o atraso (colher de cha) antes do inicio da sessao |
| `/chrono reset <nome>` | Zera o tempo jogado hoje (funciona com jogadores offline) |
| `/chrono pause` | Pausa o monitoramento globalmente |
| `/chrono resume` | Retoma o monitoramento |

## Permissoes

| Permissao | Descricao |
|---|---|
| `chronostore.admin` | Acesso a todos os comandos |

OPs tem acesso irrestrito independente de permissoes.

## Banco de dados

Tres tabelas:

- `config` — estado global (ultimo reset, pausa, momento da ultima pausa)
- `players` — dados por jogador (limite, tbsp, tempo jogado hoje, flag de monitoramento)
- `sessions` — historico de sessoes com timestamp de entrada e saida

## Estrutura do projeto

```
src/main/java/dev/soranzo/
  ChronoStore.java       ponto de entrada, schedulers, logica de pausa
  ChronoCommand.java     registro e execucao dos comandos Brigadier
  PlayerListener.java    eventos de join e quit
  Database.java          acesso ao SQLite via JDBC
  SessionManager.java    sessoes ativas em memoria
  SessionData.java       record de sessao (startTime, timePlayedToday, timeLimit)
  PlayerData.java        record de dados do jogador
  Leaderboard.java       scoreboard lateral e tab list
  Notifier.java          mensagens, sons, action bar, title
```
