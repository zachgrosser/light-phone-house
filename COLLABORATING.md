# Working on this repo together

This guide covers three things: how to invite someone, how they get set up, and the everyday steps for working without stepping on each other's toes.

---

## 1. Invite a collaborator (repo owner does this once)

1. Go to **github.com/zachgrosser/light-phone-house**.
2. Click **Settings**, the ⚙️ tab at the top of the repo.
3. In the left sidebar, click **Collaborators**. GitHub may ask for your password or 2FA code.
4. Click **Add people**, type your friend's **GitHub username** (or email), select them and click **Add … to this repository**.

That's it. They get an invite by email, and it **expires after 7 days**. If it runs out, just send a new one.

As a collaborator on a personal repo, your friend can read the code, push changes, open pull requests and manage issues. They **can't** change the repo's settings, delete it or invite other people. Only the owner can.

## 2. Accept the invite and get set up (collaborator does this once)

1. Open the invite email, or go straight to
   **https://github.com/zachgrosser/light-phone-house/invitations**, and click **Accept invitation**.
2. Install [Android Studio](https://developer.android.com/studio), [git](https://git-scm.com/downloads) and the [GitHub CLI](https://cli.github.com/).
3. Log in to GitHub from the terminal. It opens your browser to confirm:
   ```bash
   gh auth login
   ```
4. Download the repo and connect it to Light's SDK for updates:
   ```bash
   gh repo clone zachgrosser/light-phone-house
   cd light-phone-house
   git remote add upstream https://github.com/lightphone/light-sdk.git
   ```
5. Open the folder in Android Studio and follow [DEVELOPING.md](DEVELOPING.md) to install Java 17 and set up the emulator.

> Prefer clicking to typing? [GitHub Desktop](https://desktop.github.com/) does everything below with buttons, and so does Android Studio's **Git** menu. The ideas are the same.

---

## 3. The everyday workflow

The golden rule: **nobody edits `main` directly.** Each piece of work happens on its own *branch* (a private copy you can change freely). It goes back into `main` through a *pull request*, which the other person looks over first.

```
main  ──●────────────●──────────●──▶
         \          /          /
          ●──●──●──┘  (zach)  /
           \                 /
            ●──●──●─────────┘ (friend)
```

### Start something new

```bash
git checkout main
git pull                                   # get everyone's latest work
git checkout -b add-timer-screen           # make a branch; name it after what you're doing
```

### Save your work as you go

```bash
git add .                                  # stage everything you changed
git commit -m "Add a basic timer screen"   # save a snapshot with a short note
git push -u origin add-timer-screen        # upload the branch (later pushes: just `git push`)
```

Commit little and often. Each commit is a checkpoint you can go back to.

### Ask for it to be merged: open a pull request

```bash
gh pr create --fill
```

Or open the repo on GitHub and click the yellow **Compare & pull request** banner. Fill in the short checklist it shows. GitHub then runs our **Tool** check (tests and builds) and Light's **PR Check**, and you'll see a ✅ or ❌ on each; [DEVELOPING.md §4](DEVELOPING.md#4-what-github-checks) explains them.

Before opening one, run the tests (`./gradlew :tool:testDebugUnitTest -DlightSdk.toolOnly=true`) and look at your screens in `HousePreviews.kt` at both sizes.

### Review your partner's pull request

- On GitHub: open **Pull requests**, pick theirs, then open **Files changed** and leave comments. When you're happy, click **Review changes**, then **Approve**.
- To try their code on your own machine:
  ```bash
  gh pr checkout 3          # 3 = the pull request number
  ```

### Merge it

Once it's approved and the check is green, click **Squash and merge** on GitHub, then **Delete branch**. Or run:

```bash
gh pr merge --squash --delete-branch
```

**Exception: a pull request that brings in Light's SDK** (like `sdk-update`, below) must use **Create a merge commit** instead (the arrow next to the green button). Squashing it hides Light's history from git, and the next SDK update then clashes with every file Light changed.

Then everyone runs `git checkout main && git pull` to catch up.

---

## 4. Team habits that save headaches

- **Pull before you start.** Always run `git pull` on `main` before making a new branch.
- **Keep pull requests small.** One feature or fix each makes them easier to review and less likely to clash.
- **Say what you're working on.** Use GitHub **Issues** as a shared to-do list. Assign yourself before starting so you don't both build the same thing.
- **Merge conflicts aren't scary.** If git says there's a conflict, it means you both changed the same lines. Open the file, keep the right version (Android Studio has a nice tool for this), then `git add` and `git commit`.
- **Never commit secrets.** Keep API keys and passwords out of the repo. `local.properties` and `signer/keys/` are already ignored.

## 5. Pulling in Light's SDK updates

Every so often, one of you merges Light's latest SDK on a branch and opens a pull request like any other change. The commands are in [DEVELOPING.md §6](DEVELOPING.md#6-getting-lights-latest-sdk).

## 6. Careful areas

- **The access token.** House stores it on the phone and sends it only to the person's Home Assistant. Never log it, show it on screen, or send it anywhere else. Test with a token from a test Home Assistant, never your real one in screenshots or issues.
- **What a tap does** lives in `Models.kt` (`tapAction`). Keep anything that could be risky by mistake (locks, alarms) out, and keep `ModelsTest` passing.
- **`tool/lighttool.toml`:** the `id` can never change once House is published, and `serverPackage` must stay `com.lightos` (Light's build rejects anything else).
- **Outside `tool/`:** leave Light's files alone so their updates merge cleanly. Suggestions for the SDK itself go to Light (below).

## 7. Good to know

- **Protecting `main`:** GitHub can block direct pushes to `main` with a setting called *branch protection*. On a free account that only works for **public** repos. While this repo is private, the golden rule above is a team agreement. Once it's public, turn it on under **Settings → Branches**.
- **Contributing to Light's SDK itself** is a different process: issues and pull requests on [lightphone/light-sdk](https://github.com/lightphone/light-sdk). Read their [CONTRIBUTING.md](CONTRIBUTING.md) first. They ask that issues and PRs be written by a human, and that you can explain any code change in your own words.
