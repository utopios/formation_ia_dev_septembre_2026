# Serveur MCP `atlassian` — les outils qu'il expose

Le `mcp.json` du dépôt le lance avec `READ_ONLY_MODE: "false"`. Regardez la section Écriture en gardant cette ligne en tête.

**98 outils au total : 57 de lecture, 41 d'écriture.**

> Le classement lecture / écriture est fait sur le **nom** de l'outil
> (préfixes `get`, `list`, `search` contre `create`, `update`, `delete`...).
> C'est une heuristique. Un outil dont le nom ne dit pas ce qu'il fait est
> déjà un résultat de cartographie.

## Écriture — ce que l'agent pourrait *faire*

| Outil | Ce qu'il fait |
|---|---|
| `jira_add_watcher` | Add a user as a watcher to a Jira issue. |
| `jira_remove_watcher` | Remove a user from watching a Jira issue. |
| `jira_create_issue` | Create a new Jira issue with optional Epic link or parent for subtasks |
| `jira_batch_create_issues` | Create multiple Jira issues in a batch. |
| `jira_batch_get_changelogs` | Get changelogs for multiple Jira issues (Cloud only). |
| `jira_update_issue` | Update an existing Jira issue including changing status, adding Epic l |
| `jira_assign_issue` | Assign a Jira issue to a user using the dedicated assignment endpoint. |
| `jira_delete_issue` | Delete an existing Jira issue. |
| `jira_move_issue` | Move a Jira issue to a different project (Jira Cloud only). |
| `jira_add_comment` | Add a comment to a Jira issue. |
| `jira_edit_comment` | Edit an existing comment on a Jira issue. |
| `jira_add_worklog` | Add a worklog entry to a Jira issue. |
| `jira_link_to_epic` | Link an existing issue to an epic. |
| `jira_create_issue_link` | Create a link between two Jira issues. |
| `jira_create_remote_issue_link` | Create a remote issue link (web link or Confluence link) for a Jira is |
| `jira_remove_issue_link` | Remove a link between two Jira issues. |
| `jira_transition_issue` | Transition a Jira issue to a new status. |
| `jira_create_sprint` | Create Jira sprint for a board. |
| `jira_update_sprint` | Update jira sprint. |
| `jira_add_issues_to_sprint` | Add issues to a Jira sprint. |
| `jira_move_issues_to_backlog` | Move issues to the backlog, removing them from any sprint. |
| `jira_create_customer_request` | Create a Jira Service Management customer request. |
| `jira_create_version` | Create a new fix version in a Jira project. |
| `jira_batch_create_versions` | Batch create multiple versions in a Jira project. |
| `jira_update_version` | Update an existing fix version in a Jira project. |
| `jira_update_proforma_form_answers` | Update form field answers using the Jira Forms REST API. |
| `confluence_add_label` | Add label to Confluence content (pages, blog posts, or attachments). |
| `confluence_create_page` | Create a new Confluence page. |
| `confluence_update_page` | Update an existing Confluence page. |
| `confluence_update_page_section` | Update a single section of a Confluence page without affecting the res |
| `confluence_delete_page` | Delete an existing Confluence page. |
| `confluence_move_page` | Move a Confluence page to a new parent or space. |
| `confluence_add_comment` | Add a comment to a Confluence page. |
| `confluence_reply_to_comment` | Reply to an existing comment thread on a Confluence page. |
| `confluence_add_inline_comment` | Add an inline comment anchored to a text selection on a page. |
| `confluence_upload_attachment` | Upload an attachment to Confluence content (page or blog post). |
| `confluence_upload_attachments` | Upload multiple attachments to Confluence content in a single operatio |
| `confluence_delete_attachment` | Permanently delete an attachment from Confluence. |
| `confluence_create_page_from_template` | Create a new Cloud page pre-populated with a template's body. |
| `confluence_set_page_restrictions` | Set view and edit restrictions on a Confluence page. |
| `confluence_copy_page` | Copy a Confluence page to a new location. |

## Lecture — ce que l'agent pourrait *voir*

| Outil | Ce qu'il fait |
|---|---|
| `jira_get_user_profile` | Retrieve profile information for a specific Jira user. |
| `jira_search_assignable_users` | Search Jira users assignable in a given project or issue. |
| `jira_get_issue_watchers` | Get the list of watchers for a Jira issue. |
| `jira_get_issue` | Get details of a specific Jira issue. |
| `jira_search` | Search Jira issues using JQL (Jira Query Language). |
| `jira_search_fields` | Search Jira fields by keyword with fuzzy match. |
| `jira_get_field_options` | Get allowed option values for a custom field. |
| `jira_get_project_issues` | Get all issues for a specific Jira project. |
| `jira_get_transitions` | Get available status transitions for a Jira issue. |
| `jira_get_worklog` | Get worklog entries for a Jira issue. |
| `jira_download_attachments` | Download attachments from a Jira issue. |
| `jira_get_issue_images` | Get all images attached to a Jira issue as inline image content. |
| `jira_get_agile_boards` | Get jira agile boards by name, project key, or type. |
| `jira_get_board_issues` | Get all issues linked to a specific board filtered by JQL. |
| `jira_get_sprints_from_board` | Get jira sprints from board by state. |
| `jira_get_sprint_issues` | Get jira issues from sprint. |
| `jira_get_link_types` | Get all available issue link types. |
| `jira_get_project_issue_types` | Get available issue types for a Jira project. |
| `jira_get_create_fields` | Get fields available for creating an issue of a specific type. |
| `jira_get_project_versions` | Get all fix versions for a specific Jira project. |
| `jira_get_project_components` | Get all components for a specific Jira project. |
| `jira_get_all_projects` | Get all Jira projects accessible to the current user. |
| `jira_search_projects` | Search for Jira projects by name or key prefix. |
| `jira_get_project_fields` | Get the fields available on issues of a project (the create schema), |
| `jira_get_service_desk_for_project` | Get the Jira Service Desk associated with a project key. |
| `jira_get_service_desk_queues` | Get queues for a Jira Service Desk. |
| `jira_get_queue_issues` | Get issues from a Jira Service Desk queue. |
| `jira_get_request_types` | Get request types for a Jira Service Management service desk. |
| `jira_get_request_type_fields` | Get field definitions for a Jira Service Management request type. |
| `jira_get_issue_proforma_forms` | Get all ProForma forms associated with a Jira issue. |
| `jira_get_proforma_form_details` | Get detailed information about a specific ProForma form. |
| `jira_get_issue_dates` | Get date information and status transition history for a Jira issue. |
| `jira_get_issue_sla` | Calculate SLA metrics for a Jira issue. |
| `jira_get_issue_development_info` | Get development information (PRs, commits, branches) linked to a Jira |
| `jira_get_issues_development_info` | Get development information for multiple Jira issues. |
| `jira_get_project_epic_hierarchy` | Group a project's epics under their cross-project parent issues. |
| `jira_get_cross_project_dependencies` | Find all cross-project issue links for a project. |
| `confluence_search` | Search Confluence content using simple terms or CQL. |
| `confluence_get_page` | Get content of a specific Confluence page by its ID, or by its title a |
| `confluence_get_page_children` | Get child pages and folders of a specific Confluence page. |
| `confluence_get_space_page_tree` | Get page hierarchy for a Confluence space as a flat list. |
| `confluence_get_comments` | Get comments for a specific Confluence page. |
| `confluence_get_labels` | Get labels for Confluence content (pages, blog posts, or attachments). |
| `confluence_get_inline_comments` | Get all inline comments for a Confluence page. |
| `confluence_search_user` | Search Confluence users using CQL (Cloud) or group member API (Server/ |
| `confluence_get_page_history` | Get a historical version of a specific Confluence page. |
| `confluence_get_page_diff` | Get a unified diff between two versions of a Confluence page. |
| `confluence_get_page_views` | Get view statistics for a Confluence page. |
| `confluence_get_attachments` | List all attachments for a Confluence content item (page or blog post) |
| `confluence_download_attachment` | Download an attachment from Confluence as an embedded resource. |
| `confluence_download_content_attachments` | Download all attachments for a Confluence content item as embedded res |
| `confluence_get_page_images` | Get all images attached to a Confluence page as inline image content. |
| `confluence_list_page_templates` | List Confluence page content templates. |
| `confluence_get_page_template` | Get a Cloud page template by ID, including its storage-format body. |
| `confluence_get_page_restrictions` | Get view and edit restrictions for a Confluence page. |
| `confluence_check_content_permissions` | Check whether a user or group can perform an operation on specific con |
| `confluence_get_space_permissions` | List all permission assignments for a Confluence space. |
