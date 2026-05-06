# 🎮 Secure Tris - Multiplayer Tic Tac Toe con Autenticazione

## 📋 Descrizione del Progetto

**Secure Tris** è un'applicazione web per giocare a **Tris (Tic Tac Toe) in modalità multiplayer** con sistema di autenticazione integrato. L'applicazione consente ai giocatori autenticati di sfidare altri utenti online, inviare e ricevere inviti di gioco, e tracciare le statistiche personali (vittorie, sconfitte e pareggi).

L'architettura è costruita con **Spring Boot** e utilizza un sistema di **sessioni HTTP** per gestire lo stato del gioco in tempo reale tra i giocatori connessi.

---

## 🏗️ Architettura e Logica di Business

### 1. **Flusso Autenticazione**

L'applicazione utilizza **Spring Security 6** con un database utenti in memoria (in-memory):

| Username | Password  | Ruolo |
| -------- | --------- | ----- |
| franco   | guile2026 | USER  |
| mattia   | mattia123 | USER  |
| patrizio | pat123    | USER  |
| admin    | admin123  | ADMIN |

**Flusso:**

```
GET /login → Form Login → POST /login → Spring Security valida credenziali
                                       ↓
                        Login erfolgt → redirect a /dashboard
```

**Protezione risorse:**

- **Pubbliche**: `/`, `/login`, `/public-info`, assets statici (`/css/**`, `/js/**`, `/images/**`)
- **Protette**: Tutto il resto richiede autenticazione (ruolo USER o ADMIN)
- **Errore accesso**: `/access-denied` (errore 403)

---

### 2. **Logica Multiplayer**

#### **Struttura dati in memoria:**

L'applicazione utilizza **ConcurrentHashMap** thread-safe per gestire:

```java
usersBySession          // Utenti online | sessionId → SessionUser
invites                 // Inviti di gioco | inviteId → GameInvite
games                   // Partite attive | gameId → MultiplayerGame
sessionToGame           // Mapping sessione → partita | sessionId → gameId
scores                  // Punteggi per sessione | sessionId → SessionScore
```

#### **Ciclo di vita di una partita:**

1. **Ricerca Giocatori Online** (`populateDashboard`)
   - Registra la sessione dell'utente corrente
   - Filtra utenti inattivi (timeout > 5 minuti)
   - Elenca utenti disponibili per inviti

2. **Invio Invito** (`sendInvite`)
   - Verifica che il giocatore non sia già in partita
   - Verifica che non esista già un invito pendente tra i due giocatori
   - Crea un nuovo `GameInvite` con stato PENDING
   - TTL (Time To Live) invito: 10 minuti

3. **Risposta Invito** (`respondInvite`)
   - Se accettato: crea una nuova `MultiplayerGame` con il primo giocatore come X
   - Se rifiutato: marca invito come REJECTED
   - Sincronizzazione su oggetto `GameInvite` per thread-safety

4. **Gioco in Corso** (`playMove`, `gameSnapshotFor`)
   - Board 3x3 rappresentato come array di 9 caratteri: `['X', 'O', '-', ...]`
   - Posizioni: `0-8` (left-to-right, top-to-bottom)
   - Turni alternati tra i giocatori
   - Sincronizzazione con `ReentrantLock` su `MultiplayerGame`
   - Controllo vittoria: 8 combinazioni vincenti (3 righe + 3 colonne + 2 diagonali)
   - Controllo pareggio: board pieno senza vincitori

5. **Fine Partita**
   - **Vittoria**: `GameStatus.WON` → incrementa `SessionScore.wins` del vincitore
   - **Sconfitta**: `GameStatus.LOST` → incrementa `SessionScore.losses` del perdente
   - **Pareggio**: `GameStatus.DRAW` → incrementa `SessionScore.draws` di entrambi
   - Opzione di riavviare la partita o ritornare alla lobby

---

### 3. **Gestione Sessioni**

Ogni utente online è rappresentato da:

```java
@Data
class SessionUser {
    String sessionId;        // ID sessione HTTP
    String username;         // Nome utente
    long lastActivityEpochMs; // Timestamp ultima attività (per timeout)
}
```

**Cleanup automatico**: Ogni 5 minuti, gli utenti inattivi vengono rimossi dalla lista attivi.

---

## 🛣️ Routing e Connessione con la Business Logic

### **WebController** - Endpoints disponibili

#### **Pagine HTML (Thymeleaf)**

| Rotta            | Metodo | Handler           | Logica                                                                        |
| ---------------- | ------ | ----------------- | ----------------------------------------------------------------------------- |
| `/`              | GET    | `index()`         | Homepage pubblica, mostra nome "franco"                                       |
| `/login`         | GET    | `mLogin()`        | Form login (gestito da Spring Security)                                       |
| `/dashboard`     | GET    | `mDashboard()`    | **Hub principale** - chiama `MultiplayerTicTacToeService.populateDashboard()` |
| `/access_denied` | GET    | `mAccessDenied()` | Pagina errore 403 (accesso negato)                                            |

#### **Azioni di Gioco**

| Rotta                             | Metodo | Handler                  | Logica                                                     |
| --------------------------------- | ------ | ------------------------ | ---------------------------------------------------------- |
| `/click/{index}`                  | GET    | `clickCell(index)`       | Gioca mossa sulla cella `index` (0-8) → redirect dashboard |
| `/lobby/invite/{sessionId}`       | GET    | `sendInvite(sessionId)`  | Invia invito a giocatore con sessionId                     |
| `/lobby/invite/{inviteId}/accept` | GET    | `acceptInvite(inviteId)` | Accetta invito e crea partita                              |
| `/lobby/invite/{inviteId}/reject` | GET    | `rejectInvite(inviteId)` | Rifiuta invito                                             |
| `/game/reset`                     | GET    | `resetGame()`            | Riavvia partita attuale                                    |
| `/game/leave`                     | GET    | `leaveGame()`            | Abbandona partita corrente → ritorna a lobby               |

#### **API REST (JSON)**

| Rotta                      | Metodo | Handler                 | Risposta                                      |
| -------------------------- | ------ | ----------------------- | --------------------------------------------- |
| `/api/game/state`          | GET    | `gameState()`           | Stato partita corrente (board, turno, status) |
| `/api/game/{gameId}/state` | GET    | `gameStateById(gameId)` | Stato di una partita specifica                |
| `/api/lobby/invites`       | GET    | `incomingInvites()`     | Lista inviti in arrivo (JSON)                 |

### **Diagramma Flusso Principale**

```
┌─────────────────────────────────────────────────────────────────┐
│                    GET /dashboard (Autenticato)                  │
│                         WebController                             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
                    (Principal principal)
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│   MultiplayerTicTacToeService.populateDashboard()                │
│                                                                   │
│  1. Registra sessionId e username                               │
│  2. Rimuove utenti inattivi (>5 min)                           │
│  3. Recupera lista utenti attivi                               │
│  4. Lista inviti in arrivo (PENDING)                           │
│  5. Lista inviti in uscita                                     │
│  6. Carica stato partita corrente (se esiste)                  │
│  7. Legge statistiche (wins, losses, draws)                    │
│  8. Imposta cookie lastActivity e lastGame                     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
                       Model (dati per view)
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│         Render dashboard.html (Thymeleaf Template)               │
│                                                                   │
│  - Lista utenti online con bottone "Invita"                    │
│  - Inviti ricevuti (Accetta/Rifiuta)                           │
│  - Inviti inviati (In sospeso)                                 │
│  - Board Tris (se partita attiva)                              │
│  - Statistiche personali (Win/Loss/Draw)                       │
│  - Bottoni: Riavvia partita, Abbandona partita                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 Tecnologie Utilizzate

### **Backend**

- **Spring Boot 4.0.6** - Framework web enterprise
- **Spring Security 6** - Autenticazione e autorizzazione
- **Spring Web MVC** - Controller e routing
- **Jakarta Servlet API** (successore di javax.servlet) - HTTP session management
- **Project Lombok** - Generazione automatica getter/setter/constructor
- **Java 21 LTS** - Linguaggio di programmazione

### **Frontend**

- **Thymeleaf** - Template engine per HTML dinamico
- **Thymeleaf Security Extras** - Integrazione con Spring Security nei template
- **HTML5 + CSS** - Markup e styling

### **Build & Deployment**

- **Apache Maven** - Build automation
- **Spring Boot Maven Plugin** - Packaging e esecuzione
- **Maven Compiler Plugin** - Compilazione Java

### **Strutture Dati**

- **ConcurrentHashMap** - Cache thread-safe per utenti, inviti, partite
- **ReentrantLock** - Sincronizzazione critiche nei giochi
- **UUID** - Generazione ID univoci per inviti e partite

---

## 🔐 Configurazione Sicurezza

### **SecurityConfig.java**

Definisce la politica di autenticazione e autorizzazione:

```java
✓ Risorse Pubbliche (senza login):
  - GET / (homepage)
  - GET /login (form login)
  - GET /public-info
  - /css/**, /js/**, /images/** (assets statici)

✓ Risorse Protette (USER o ADMIN):
  - /dashboard, /click/*, /lobby/*, /game/*, /api/*

✓ Password Encoder:
  - BCrypt (algoritmo di hashing sicuro)

✓ Logout:
  - POST /logout → redirect a /login?logout
```

---

## 📊 Modelli Dati Principali

### **GameInvite**

```java
inviteId           // UUID unico
inviterSessionId   // Chi ha inviato
inviterUsername
invitedSessionId   // Chi ha ricevuto
invitedUsername
status             // PENDING, ACCEPTED, REJECTED
createdAtEpochMs   // Timestamp creazione (TTL: 10 min)
```

### **MultiplayerGame**

```java
gameId              // UUID unico
playerXSessionId    // Giocatore X
playerXUsername
playerOSessionId    // Giocatore O
playerOUsername
board[]             // Array 9 elementi: ['X','O','-',...]
turnSessionId       // Chi gioca ora
roundStarterSessionId // Chi ha iniziato questo round
status              // IN_PROGRESS, WON, LOST, DRAW
winnerSessionId     // Sessione vincitore (se status=WON)
message             // Messaggio stato (es: "Tocca a X")
updatedAtEpochMs    // Timestamp ultimo aggiornamento
lock                // ReentrantLock per sincronizzazione thread
```

### **SessionScore**

```java
wins    // AtomicInteger - numero vittorie
losses  // AtomicInteger - numero sconfitte
draws   // AtomicInteger - numero pareggi
```

---

## 🚀 Come Eseguire

### **Requisiti**

- Java 21+ (LTS)
- Maven 3.6+

### **Avvio Applicazione**

```bash
mvn spring-boot:run
```

**L'applicazione sarà disponibile su**: `http://localhost:8080`

### **Compilazione JAR**

```bash
mvn clean package
java -jar target/secure-0.0.1-SNAPSHOT.jar
```

### **Credenziali Test**

| Utente   | Password  |
| -------- | --------- |
| franco   | guile2026 |
| mattia   | mattia123 |
| patrizio | pat123    |
| admin    | admin123  |

---

## 📁 Struttura Progetto

```
src/main/
├── java/it/gol8/secure/
│   ├── SecureApplication.java         # Classe principale Spring Boot
│   ├── config/
│   │   └── SecurityConfig.java        # Configurazione autenticazione
│   ├── controller/
│   │   └── WebController.java         # Routing e endpoints
│   ├── model/
│   │   ├── GameInvite.java            # Modello invito
│   │   ├── MultiplayerGame.java       # Modello partita
│   │   ├── GameStatus.java            # Enum stati partita
│   │   ├── InviteStatus.java          # Enum stati invito
│   │   └── SessionUser.java           # Modello utente online
│   └── service/
│       ├── MultiplayerTicTacToeService.java # Logica multiplayer
│       └── TicTacToeService.java            # Logica single-player (legacy)
└── resources/
    ├── application.properties         # Configurazione server
    └── templates/
        ├── index.html                 # Homepage
        ├── login.html                 # Form login
        ├── dashboard.html             # Hub partite (principale)
        ├── 403.html                   # Errore accesso
        ├── admin-only.html            # Pagina admin
        └── public-info.html           # Info pubbliche
```

---

## ⚙️ Configurazione Server

**File**: `application.properties`

```properties
spring.application.name=secure
server.address=0.0.0.0              # Ascolta su tutte le interfacce
server.port=${SERVER_PORT:8080}     # Porta (default 8080)
```

---

## 🔄 Ciclo di Vita di una Partita - Esempio Pratico

### **Scenario**: Franco sfida Mattia

**1. Fase Lobby**

```
[Franco accede] → GET /dashboard
  ↓
  SessionUser(franco, sessionId='session-franco')
  Vede: [Mattia disponibile]

[Mattia accede] → GET /dashboard
  ↓
  SessionUser(mattia, sessionId='session-mattia')
  Vede: [Franco disponibile]
```

**2. Invio Invito**

```
Franco → GET /lobby/invite/session-mattia
  ↓
  GameInvite(inviteId='inv-123',
             inviter=franco,
             invited=mattia,
             status=PENDING)
  ↓
  Mattia → GET /dashboard
    ↓
    Vede: "Franco ti ha invitato!" [Accetta] [Rifiuta]
```

**3. Accettazione Invito**

```
Mattia → GET /lobby/invite/inv-123/accept
  ↓
  MultiplayerGame(gameId='game-456',
                  playerX='franco',
                  playerO='mattia',
                  board=['-','-','-','-','-','-','-','-','-'],
                  turn='franco',
                  status=IN_PROGRESS)
  ↓
  sessionToGame['session-franco'] = 'game-456'
  sessionToGame['session-mattia'] = 'game-456'
```

**4. Gioco in Corso**

```
Franco → GET /click/4 (centro)
  ↓
  Valida: turno=franco, cella 4 libera
  board[4] = 'X'
  turnSessionId = 'session-mattia'

Mattia → GET /click/0 (angolo)
  ↓
  Valida: turno=mattia, cella 0 libera
  board[0] = 'O'
  turnSessionId = 'session-franco'

... (altre mosse) ...

Franco → GET /click/2
  ↓
  board = ['O','X','X','O','X','O','X','O','X']
  Controllo vincita: [0,3,6] = O,O,O ✓
  status = WON (mattia vince!)
  scores[session-mattia].losses++
  scores[session-franco].wins++
```

**5. Fine Partita**

```
Entrambi vedono:
  - "Mattia ha vinto! 🎉"
  - Board disabilitato (no click)
  - Bottoni: [Riavvia] [Torna a Lobby]
```

---

## 🛡️ Caratteristiche di Sicurezza

✅ **Autenticazione**: Spring Security con password BCrypt  
✅ **Sessioni HTTP**: ID sessione unico per ogni utente  
✅ **CSRF Protection**: Abilitata di default in Spring Security  
✅ **Thread-Safety**: ConcurrentHashMap + ReentrantLock su partite critiche  
✅ **Timeout Sessione**: Utenti inattivi rimossi dopo 5 minuti  
✅ **Validazione Turni**: Impossibile giocare fuori dal proprio turno  
✅ **Validazione Mosse**: Impossibile sovrascrivere una cella occupata

---

## 📝 Note di Sviluppo

- L'applicazione è **stateful**: usa memoria in-heap per partite e inviti
- **Non persistente**: i dati si perdono al riavvio dell'app
- **Singolo istante**: non supporta multi-istanza (load balancing)
- **Legacy**: Contiene `TicTacToeService` per modalità single-player non utilizzata

### **Futuri Miglioramenti**

- Database persistente (PostgreSQL/MongoDB)
- WebSocket per aggiornamenti real-time
- Supporto multi-istanza con Redis
- Matchmaking automatico
- Sistema di ranking ELO
- Chat in gioco

---

## 📄 Licenza

Progetto accademico - Università dell'Informatica (Accademia Informatica)

---

**Autore**: Franco (sviluppatore principale)  
**Data Creazione**: 2026  
**Versione**: 0.0.1-SNAPSHOT
