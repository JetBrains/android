<?xml version="1.0"?>
<recipe>

    <copy from="root/TestTargetResourceFile.xml"
                to="values/TestTargetResourceFile.xml" />
    <merge from="root/values/strings.xml"
             to="values/TestTargetResourceFile.xml" />

</recipe>
