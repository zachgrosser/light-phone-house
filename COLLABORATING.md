# Working together

## Inviting a collaborator

The repo owner goes to Settings → Collaborators → Add people and enters the person's GitHub username. The invite expires after 7 days.

Collaborators can push branches and review. Anyone else can contribute from a fork.

## Getting set up

```bash
gh auth login
gh repo clone zachgrosser/light-phone-house
cd light-phone-house
git remote add upstream https://github.com/lightphone/light-sdk.git
```

Then follow [DEVELOPING.md](DEVELOPING.md).

If you're contributing from a fork, click **Fork** on GitHub first and clone your fork instead.

## Day to day

Nobody pushes to `main` directly. Each change goes on its own branch and comes in through a pull request.

```bash
git checkout main && git pull
git checkout -b add-brightness
# ...make changes...
git add .
git commit -m "Add brightness levels for lights"
git push -u origin add-brightness
gh pr create --fill
```

Run the tests before opening the pull request. To try someone else's pull request locally, use `gh pr checkout <number>`.

## Merging

`main` is protected: pull requests need the **Tool** check to pass and one approval. The owner can skip the approval on their own pull requests.

Use **Squash and merge** for normal changes. Pull requests that bring in Light's SDK use **Create a merge commit** instead.

## For maintainers

The first time someone contributes from a fork, GitHub waits for you to approve their checks before running them. Look over the changes before you do.

## Tips

- Pull `main` before starting something new.
- Keep pull requests small.
- Comment on an issue before you start, so two people don't build the same thing.
- Never commit tokens or passwords. `local.properties` is already ignored.
- Changes to Light's SDK itself go to [lightphone/light-sdk](https://github.com/lightphone/light-sdk).
