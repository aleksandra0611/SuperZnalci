# SuperZnalci

SuperZnalci je mobilna aplikacija za Android za učenje s kratkimi vprašanji in igro, po zgledu Duolinga, 
namenjena šolski snovi. Aplikacija združuje idejo učilnice (kot Google Classroom) in igro s točkami (kot 
Duolingo): učitelj odpre učilnico za svoj razred in zastavlja vprašanja, učenci se vpišejo s kodo, 
odgovarjajo in tekmujejo na lestvici. 

## Tehnologije
 
| Del | Tehnologija |
|---|---|
| Mobilna aplikacija | Kotlin, Android Studio, XML + ViewBinding |
| Arhitektura aplikacije | MVVM + Clean Architecture, Coroutines + Flow, Hilt, Retrofit |
| Strežnik (backend) | Node.js + Express (JavaScript), REST API |
| Baza podatkov | MongoDB (Atlas) + Mongoose |
| Prijava | E-pošta in geslo, bcrypt, JWT žeton, vlogi TEACHER in STUDENT |
| Orodja | Git + GitHub, Jira, Postman, MongoDB Compass |

## Struktura projekta
 
```
/backend     Node.js + Express strežnik (REST API)
/frontend         Android aplikacija (Kotlin + XML)
/Documents  Makete + dokumentacija projekta    
```

## Zahteve
 
Pred zagonom mora biti nameščeno:
 
- **Node.js** 20 ali novejši (z npm)
- **Android Studio** z Android SDK (min. Android 8.0, API 26) in vsaj enim emulatorjem (AVD)
- **JDK 17** (priložen Android Studiu)
- **MongoDB Compass** (neobvezno, za pregled baze)
- Dostop do MongoDB Atlas baze (povezovalni niz dobiš od ekipe)

## 1. Zagon strežnika (backend)
 
### Namestitev
 
```bash
git clone https://github.com/aleksandra0611/SuperZnalci.git
cd SuperZnalci/backend
npm install
```
 
Ukaz `npm install` prebere datoteko `package.json` in sam namesti vse potrebne module (npr. express, mongoose, dotenv, jsonwebtoken, bcrypt, node-cron). Posameznih modulov ni treba nameščati ročno.

### Konfiguracija (.env)
 
V mapi `backend` ustvari datoteko `.env` in vnesi svoje vrednosti:
 
```dotenv
PORT=3000
MONGODB_URI=mongodb+srv://<uporabnik>:<geslo>@<cluster>.mongodb.net/super_znalci
JWT_SECRET=<dolg-nakljucen-niz>
 
# Lokalni razvoj
NODE_ENV=development
 
# Skrivnost za HMAC ponastavitvenih kod
RESET_CODE_SECRET=<dolg-nakljucen-niz>
 
# Lokalno testiranje brez pošiljanja e-pošte (kode se izpišejo v konzolo)
MAIL_MODE=console
 
# Za pravo pošiljanje e-pošte pozneje:
# MAIL_MODE=smtp
# SMTP_HOST=
# SMTP_PORT=587
# SMTP_USER=
# SMTP_PASS=
# SMTP_FROM=
```

| Spremenljivka | Pomen |
|---|---|
| `PORT` | Vrata, na katerih teče strežnik |
| `MONGODB_URI` | Povezovalni niz za MongoDB Atlas |
| `JWT_SECRET` | Skrivnost za podpis JWT žetonov |
| `RESET_CODE_SECRET` | Skrivnost za HMAC ponastavitvenih kod gesla |
| `NODE_ENV` | `development` za lokalni razvoj |
| `MAIL_MODE` | `console` (izpis v konzolo) ali `smtp` (prava pošta) |
 
**Pomembno:** datoteka `.env` vsebuje skrivnosti in **ne sme biti v repozitoriju**.

### Zagon
 
```bash
npm run dev
```
 
Če vse deluje, se v terminalu izpiše:
 
```
MongoDB povezan
Strežnik teče na portu 3000
```

Če teh dveh vrstic ni, preveri `MONGODB_URI` v `.env` in ali je tvoj IP dovoljen v MongoDB Atlas (Network Access).
 
### Pregled baze
 
Odpri **MongoDB Compass**, se poveži z istim nizom kot `MONGODB_URI` in odpri bazo `super_znalci`. Tam so zbirke (User, Classroom, Lesson, Attempt, ...).
 
### Preizkušanje API-ja
 
Endpointe lahko ročno preizkusiš s **Postmanom** na naslovu `http://localhost:3000`.

## 2. Zagon aplikacije (Android)
 
Strežnik mora že teči (korak 1).
 
1. Odpri Android Studio in zaženi emulator (Device Manager → Play).
2. V terminalu v korenski mapi Android projekta (PowerShell) zaženi:
```powershell
# 1. Sestavi debug APK
.\gradlew.bat assembleDebug
 
# 2. Preveri, kateri emulatorji so povezani (poišči ID, npr. emulator-5554)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
 
# 3. Namesti aplikacijo na emulator (zamenjaj ID emulatorja)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
```

3. Aplikacijo nato odpri na emulatorju.
Aplikacijo lahko tudi enostavno zaženeš z gumbom **Run** v Android Studiu.
 
### Povezava emulatorja s strežnikom
 
Emulator ne vidi `localhost` računalnika. Naslov `10.0.2.2` v emulatorju pomeni računalnik, na katerem teče strežnik, zato mora biti osnovni naslov API-ja v aplikaciji:
 
```
http://10.0.2.2:3000/
```
 
## Pogoste težave
 
| Težava | Rešitev |
|---|---|
| `MongoDB povezan` se ne izpiše | Preveri `MONGODB_URI` in dostop IP v MongoDB Atlas |
| `npm run dev` ne najde modulov | Zaženi `npm install` v mapi `backend` |
| Vrata 3000 so zasedena | Spremeni `PORT` v `.env` ali ustavi drug proces |
| Aplikacija ne doseže strežnika | Preveri, da strežnik teče in da aplikacija uporablja `10.0.2.2` |
| `adb devices` ne pokaže emulatorja | Najprej zaženi emulator v Android Studiu |
| `gradlew.bat` ni prepoznan | Ukaz zaženi v korenski mapi Android projekta |
 

## Ekipa
 
Elena Klincharova, Andjela Radakovic, Aleksandra Mickoska