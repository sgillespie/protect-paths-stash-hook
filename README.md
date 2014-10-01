# Protect Paths Hook
[![Build Status](https://travis-ci.org/sgillespie/protect-paths-stash-hook.svg?branch=master)](https://travis-ci.org/sgillespie/protect-paths-stash-hook)

A plugin for Atlassian Stash that protects paths from being pushed by non-administrators (for projects).

## Configuration

This hook is configured per-repository, and has the following options:

### Path Patterns

List of paths to protect. Whitespace seperated.  Regular expressions accepted.

Examples:

```
x/y/z
z/y/x
```

`x/y/z z/y/x`

`x/.*`

### Filter Branches By

The type of filter to use on the "Branches" field below.  The following options are acceptable:

 * All Branches
     * Protects paths on all branches; Branches field is ignored.
 * Include Branches
     * Protects only branches specified in the branches field.
 * Exclude Branches
     * Protects branches NOT specified in the branches field.

### Branches

List of branches to filter. Whitespace seperated; Regular expressions accepted.  Depends on "Filter Branches By" field.
Ignored on "All Branches". Specified branches are protected on "Include Branches". Specified branches are not protected
on "Exclude Branches".

Examples:

```
master
release/.*
```

`master release/.*`

`feature/.*`

### Exclude Users

Allow the specified users to push to protected paths.  White space seperated.  One reason you might want to use this
is for automated builds or merges.

Examples:

`jenkins-user`

`jenkins-user stash-user`

## Building

This project, like all Atlassian Plugins, requires the Atlassian Plugin SDK.  The TGZ distribution can be downloaded
here:

https://marketplace.atlassian.com/plugins/atlassian-plugin-sdk-tgz

To run stash locally with this plugin installed, run (in the project root):

`atlas-run`

To run unit tests, run:

`atlas-unit-test`
