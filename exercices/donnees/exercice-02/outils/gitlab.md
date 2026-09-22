# Serveur MCP `gitlab` — les outils qu'il expose

Le `mcp.json` du dépôt le lance avec `GITLAB_READ_ONLY_MODE: "true"`. Les outils d'écriture listés ici sont donc **exposés par le serveur mais bloqués par sa configuration** — deux choses différentes.

**65 outils au total : 49 de lecture, 16 d'écriture.**

> Le classement lecture / écriture est fait sur le **nom** de l'outil
> (préfixes `get`, `list`, `search` contre `create`, `update`, `delete`...).
> C'est une heuristique. Un outil dont le nom ne dit pas ce qu'il fait est
> déjà un résultat de cartographie.

## Écriture — ce que l'agent pourrait *faire*

| Outil | Ce qu'il fait |
|---|---|
| `get_merge_request_approval_state` | Get merge request approval details including approvers. Use this to in |
| `get_merge_request_conflicts` | Get the conflicts of a merge request. Use this to inspect merge confli |
| `list_merge_request_pipelines` | List pipelines for a merge request with pagination. Use this to inspec |
| `get_merge_request` | Get details of a merge request (mergeRequestIid or branchName required |
| `get_merge_request_diffs` | Get the changes/diffs of a merge request (mergeRequestIid or branchNam |
| `list_merge_request_changed_files` | List changed file paths in a merge request without diff content (merge |
| `list_merge_request_diffs` | List merge request diffs with pagination (mergeRequestIid or branchNam |
| `get_merge_request_file_diff` | Get diffs for specific files from a merge request (mergeRequestIid or |
| `list_merge_request_versions` | List all versions of a merge request. Use this for a collection of res |
| `get_merge_request_version` | Get a specific version of a merge request. Use this for a known resour |
| `get_merge_request_discussion` | Get a single discussion item for a merge request. Use this to fetch on |
| `get_merge_request_note` | Get a specific note for a merge request. Use this to fetch one known m |
| `get_merge_request_notes` | List notes for a merge request. Use this to list flat notes on a merge |
| `list_merge_request_emoji_reactions` | List all emoji reactions on a merge request. Use this for a collection |
| `list_merge_request_note_emoji_reactions` | List all emoji reactions on a merge request note. Pass discussion_id f |
| `list_merge_requests` | List merge requests (without project_id: user's MRs; with project_id: |

## Lecture — ce que l'agent pourrait *voir*

| Outil | Ce qu'il fait |
|---|---|
| `search_repositories` | Search for GitLab projects. Use this to discover matching content; cho |
| `get_file_contents` | Get contents of a file or directory from a GitLab project. Use this fo |
| `get_branch` | Get branch details (commit, protection status). Use this for a known r |
| `list_branches` | List branches in project with search filter. Use this for a collection |
| `list_protected_branches` | List protected branches in a project, supports search filter. Use this |
| `get_protected_branch` | Get details of a single protected branch (access levels, force push se |
| `get_branch_diffs` | Get diffs between two branches or commits. Use this for a known resour |
| `mr_discussions` | List discussion items for a merge request. Use this to list complete d |
| `get_draft_note` | Get a single draft note from a merge request. Use this for a known res |
| `list_draft_notes` | List draft notes for a merge request. Use this for a collection of res |
| `list_issue_emoji_reactions` | List all emoji reactions on an issue. Use this for a collection of res |
| `list_issue_note_emoji_reactions` | List all emoji reactions on an issue note. Pass discussion_id for disc |
| `list_issues` | List issues (default: created by current user; use scope='all' for all |
| `my_issues` | List issues assigned to the authenticated user. Use this for issue man |
| `get_issue` | Get details of a specific issue. Returns a slim milestone by default; |
| `list_todos` | List GitLab to-do items for the current user. Use this for a collectio |
| `list_issue_links` | List all issue links for a specific issue. Use this for a collection o |
| `list_issue_discussions` | List discussions for an issue. Use this to inspect threaded discussion |
| `get_issue_link` | Get a specific issue link. Use this for a known resource or result; ch |
| `list_namespaces` | List all namespaces (users and groups) available to the current user. |
| `get_namespace` | Get details of a namespace (user or group) by ID or path. Groups are n |
| `verify_namespace` | Verify if a namespace path exists. Use parent_id to scope the check to |
| `get_project` | Get details of a specific project. Use this for a known resource or re |
| `list_projects` | List projects accessible by the current user. Use this for a collectio |
| `list_project_members` | List members of a GitLab project. Use this for a collection of resourc |
| `list_group_members` | List members of a GitLab group with optional name or username search. |
| `list_labels` | List labels for a project. Use this for a collection of resources; cho |
| `get_label` | Get a single label from a project. Use this for a known resource or re |
| `list_group_projects` | List projects in a group. Use this for a collection of resources; choo |
| `get_repository_tree` | List files and directories in a repository. Use this for a known resou |
| `validate_ci_lint` | Validate provided GitLab CI/CD YAML content for a project. Use this to |
| `validate_project_ci_lint` | Validate an existing .gitlab-ci.yml configuration for a project. Use t |
| `list_ci_catalog_resources` | List GitLab CI/CD Catalog resources/components visible to the user. Us |
| `get_ci_catalog_resource` | Get details for a GitLab CI/CD Catalog resource, including versions an |
| `list_group_merge_requests` | List merge requests across all projects of a group and its subgroups. |
| `get_users` | Get GitLab user details by usernames. Use this for a known resource or |
| `get_user` | Get user details by ID. Use this for a known resource or result; choos |
| `whoami` | Get current authenticated user details. Use this to identify the authe |
| `list_commits` | List repository commits with filtering options. Use this for a collect |
| `get_commit` | Get details of a specific commit. Use this for a known resource or res |
| `get_commit_diff` | Get changes/diffs of a specific commit. Use this for a known resource |
| `get_file_blame` | Get git blame for a file at a given ref. Each entry maps a contiguous |
| `list_commit_statuses` | List statuses for a commit. Use this for a collection of resources; ch |
| `list_group_iterations` | List group iterations with filtering options. Use this for a collectio |
| `download_attachment` | Download an uploaded file from a project (images returned as base64; u |
| `health_check` | Verify server status and authentication. Always reports the MCP server |
| `list_events` | List events for the authenticated user (before/after: YYYY-MM-DD). Use |
| `get_project_events` | List events for a project (before/after: YYYY-MM-DD). Use this for a k |
| `discover_tools` | Discover and activate additional tool categories for this session. Ava |
