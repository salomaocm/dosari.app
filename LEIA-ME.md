# Dosari — como gerar o APK

Este é o projeto Android do Dosari (versão 2.15.3). O APK não foi gerado aqui porque o ambiente
onde o projeto foi montado não consegue baixar o Android SDK. O projeto está pronto: basta uma
das duas opções abaixo.

## Opção A — sem instalar nada (recomendada): GitHub faz o APK por você
1. Crie uma conta gratuita em github.com e um repositório **privado** (ex.: `dosari`).
2. Na página do repositório: **Add file → Upload files**. Arraste **todo o conteúdo desta pasta**
   (inclusive a pasta oculta `.github`; se o navegador não mostrar, use o GitHub Desktop ou `git push`).
   Não envie a pasta `node_modules` (ela nem está no zip).
3. Vá em **Actions → Gerar APK do Dosari → Run workflow**. Leva uns 5 a 10 minutos.
4. Quando ficar verde, abra a execução e baixe **Dosari-APK** (em *Artifacts*). Dentro do zip está o `app-release.apk`.
5. Mande o APK para o celular (WhatsApp, Drive, cabo) e toque nele. Se o Android pedir, permita
   **instalar apps desta fonte** (é o aviso normal para apps fora da Play Store).

## Opção B — no computador com Android Studio
1. Instale o Android Studio e o Node.js.
2. Nesta pasta: `npm ci` e depois `npx cap sync android`.
3. `npx cap open android` → no Android Studio: **Build → Build APK(s)** (ou **Generate Signed Bundle / APK**).
4. O APK fica em `android/app/build/outputs/apk/`. Para testar direto no celular ligado por cabo, use o botão ▶.

## Atualizar o app depois
1. Copie o novo `dosari.html` para `www/index.html` (troque o arquivo).
2. Repita a Opção A (envie o arquivo novo) ou a Opção B (`npx cap sync android` e compile).
3. Instale o APK novo **por cima** do antigo: os dados ficam. Isso funciona porque o projeto usa sempre a mesma
   chave (`android/app/dosari.keystore`). **Guarde uma cópia desse arquivo**: se perdê-lo, as atualizações
   futuras não instalam por cima e será preciso desinstalar (perdendo os dados).

## Levar as receitas da versão de navegador para o app
Os dados da versão HTML ficam no navegador, separados dos do app. Para migrar:
1. Na versão antiga: **Ajustes → Exportar backup** (gera um `.json`).
2. Envie esse `.json` ao celular e toque nele → escolha **Dosari** em "Abrir com" → confirme a restauração.

## O que é diferente no app instalado
- **Compartilhar** PDF, receita (arquivo), texto e backup abre a folha de compartilhar do Android.
- **Imprimir** some do menu de compartilhar (o Android não imprime de dentro de apps como este); use o PDF.
- **Receber**: tocar num `.json` de receita/backup e escolher Dosari, ou compartilhar a mensagem com
  o código `MC1:...` para o Dosari, importa sozinho.
- O ícone e a tela de abertura usam a identidade do Dosari (verde).

## Para quem quiser publicar na Play Store
Precisa de uma conta de desenvolvedor Google, de um pacote **.aab** (`./gradlew bundleRelease`) e de uma chave de
assinatura própria e secreta (não a deste projeto). Se quiser, peça e eu preparo.

## Observações técnicas
- Pacote: `br.com.dosari.app` · Capacitor 8 · Java 21 · minSdk 24 (Android 7+) · targetSdk 36.
- Este projeto **não foi compilado nem testado em aparelho** pelo ambiente que o criou. Se o primeiro build falhar,
  copie a mensagem de erro do passo que ficou vermelho e envie para ajustar.

## Segurança (2.12.3)
- Mantenha o repositório do GitHub **privado**: o arquivo `dosari.keystore` e a senha padrão estão nele. Quem tiver a chave pode publicar "atualizações" falsas do app.
- Para mais proteção, defina os Secrets `DOSARI_KEYSTORE_PASSWORD` e `DOSARI_KEY_PASSWORD` no GitHub (o build usa a senha padrão se não existirem) e troque a senha do keystore.

## Proteger a chave de assinatura (recomendado)
A chave `android/app/dosari.keystore` é a identidade do app: quem a tiver pode assinar atualizações como se fossem suas.
Hoje ela vem dentro do projeto (e a senha padrão está no `build.gradle`) para que o APK seja gerado sem configuração.
Por isso: **mantenha o repositório privado** e guarde uma cópia da chave fora do GitHub.
Para tirar a chave do repositório sem trocar a identidade do app (as atualizações continuam instalando por cima):
1. No computador: `base64 -w0 android/app/dosari.keystore` (no Mac: `base64 -i android/app/dosari.keystore`) e copie o resultado.
2. No GitHub: **Settings → Secrets and variables → Actions → New repository secret** e crie:
   `DOSARI_KEYSTORE_B64` (o texto copiado), `DOSARI_KEYSTORE_PASSWORD` e `DOSARI_KEY_PASSWORD` (a senha da chave).
3. O workflow passa a usar os Secrets automaticamente. Depois disso troque a senha da chave
   (`keytool -storepasswd` e `keytool -keypasswd`), atualize os Secrets e apague `dosari.keystore` e as senhas do repositório.
   Só apague do histórico do Git depois de ter a cópia da chave guardada em segurança.
**Não gere uma chave nova** se o app já está instalado: a atualização não instalaria por cima.

## Como o app guarda os dados
Os dados ficam no aparelho (IndexedDB, com cópia de contingência em localStorage). Importar receitas e restaurar backup são
operações "tudo ou nada": se a gravação falhar, os dados anteriores continuam como estavam. Faça backups com frequência em
Ajustes › Backup e restauração. O alarme dos temporizadores do app só toca com o app aberto; para avisar com o app fechado
use o atalho do relógio do celular.
