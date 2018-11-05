# Git clone project created with NPW

Simple test
----
1. Create a new basic java project with an `Empty Activity`

1. Make sure the project syncs and builds correctly

1. Create a git repository (based on [http://go/gob/users/user-repository#create-repo](http://go/gob/users/user-repository#create-repo)):
    1. `gob-ctl create user/$USER/git-smoke-test-$(date +%F)`

1. Initialize git repository on project folder and make an initial commit with all the files:
    1. `git init`
    1. `git add .`
    1. `git commit -m "First commit"`
    1. `git remote add origin sso://user/$USER/git-smoke-test-$(date +%F)`
    1. `git push origin master`

1. Clone the project from the repository into a new location using git cli
    1. `git clone sso://user/$USER/git-smoke-test-$(date +%F)`

1. Open project from cloned location
    ##### Expected results
    - Cloned project should be created correctly
    - Project can be synced and built correctly

1. If successful, you can [delete the created repository](http://go/gob/users/user-repository#delete-repo):
    1. `gob-ctl delete user/$USER/git-smoke-test-$(date +%F)`