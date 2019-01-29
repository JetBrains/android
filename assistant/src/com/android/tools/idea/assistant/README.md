# Assistant Panel Infrastructure

This document describes the purpose, capabilities and usage of the Android Studio
Assistant infrastructure.

NOTE: As the feature set of this infrastructure grows, method signatures and
behavior may change across Studio releases. 

[TOC]

## Purpose

Provide a simple and consistent way to surface "assist"-like content in a side panel
of Android Studio. In this context, "assist" may mean anything from providing in-context
documentation to displaying stateful buttons that can perform arbitrary actions on
a user's project.

## Capabilities

The assistant can display a hierarchical view of collected "tutorials", collected
under "feature" groupings. Each tutorial can display arbitrary content, though in
a rigid format, and allows stateful buttons to be connected to arbitrary code.

For example, you develop a mobile platform that supports a variety of features
(authentication, storage, etc). You may choose to provide an assistant that contains
targeted tutorials for specific scenario and capabilities under each feature. You
may also choose to expose common tasks (adding dependencies, inserting code snippets,
etc.) at the push of a button. See `Tools > Firebase` for a working example.

## Usage

1. Create a plugin that will encapsulate your assistant. It is assumed that you
   have some experience with plugins. If not, please read 
   http://www.jetbrains.org/intellij/sdk/docs/basics/getting_started.html
1. Implement `AssistantBundleCreator`. Note that there
   two approaches to this. You may return your own `TutorialBundleData` instance or
   you may return an xml configuration in the pre-defined format. The latter is less
   work and more maintainable but reduces flexibility.
   
   1. Provide a unique id to be returned by `#getBundleId`. This value will be needed
   later in your `plugin.xml`. This can be any string value you want, as long as you
   choose something that's not likely to collide with another assistant bundle later.
   For convention, you may just want to put your feature's name here.
   
   1. Return a bundle configuration.
   
      ### Option 1 - Returning a URL of a local config file
   
       If you return an xml file via `AssistantBundleCreator#getConfig` in the below form,
       it will be converted to an instance of `TutorialBundleData`. This is the recommended
       approach due to simplicity and low maintenance. However, if you need to add your
       own data to the config (as it will be available to you when executing actions), this
       may not be the best choice. 

       *** note
       An example of where you might not want to use `AssistantBundleCreator#getConfig`: Your
       `AssistantActionHandler` needs to know what to act on (can be done via 
       `ActionData#getActionArgument`) and what to apply. In this case, you may implement
       `ActionData`, adding a custom getter, `getFooValue`, and cast your `ActionData` being 
       passed in to `AssistantActionHandler#handleAction` to your custom `ActionData` so you
       may use the value.
       ***
 
       ```xml
       <?xml version="1.0"?>
       <tutorialBundle
           icon="optional_icon_filename"
           logo="optional_logo_filename_supersedes_icon"
           name="Assistant Name"
           resourceRoot="/path_from_plugin_root_to_resources">
         <welcome>
           <![CDATA[Intro Text that may contain links and basic markup]]>
         </welcome>
         <feature
             icon="optional_icon_filename"
             name="Feature Name">
           <description>
             <![CDATA[Description of feature that may contain links and basic markup]]>
           </description>
           <tutorial
               key="unique_key_used_for_mapping_and_logging"
               label="Descriptive Label"
               remoteLink="optional_url_to_web_version"
               remoteLinkLabel="label_for_optional_link">
             <description>
               <![CDATA[Description of tutorial that may contain links and basic markup]]>
             </description>
             <step label="Label For Step 1">
               <stepElement>
                 <action
                     key="unique_key_for_action"
                     label="Button Label"
                     successMessage="Optional message displayed when action is completed">
                   <!-- recipes are an optional convenience for performing common actions, see
                        com.android.tools.idea.templates.recipe.Recipe for more information.
                        Note that you will need to execute the recipe in your handler via
                        RecipeUtils#execute or similar. -->
                   <recipe>
                     <classpath mavenUrl="com.foo:bang:3.0.0"></classpath>
                     <apply plugin="com.foo.bar"></apply>
                     <dependency mavenUrl="com.foo.bop:biz:9.8.0"></dependency>
                   </recipe>
                 </action>
               </stepElement>
             </step>
             <step label="Label for Step 2">
               <stepElement>
                 <section>
                   <![CDATA[Arbitrary text that may contain simple html]]>
                 </section>
               </stepElement>
               <stepElement>
                 <code fileType="JAVA">java code</code>
               </stepElement>
             </step>
           </tutorial>
           <tutorial>
           <!-- ... -->
           </tutorial>
         </feature>
         <feature>
         <!-- ... -->
         </feature>
       </tutorialBundle>
       ```

       ### Option 2 - Return a custom version of TutorialBundleData
       
       You may return your own implementation of `TutorialBundleData` via 
       `AssistantBundleCreator#getBundle`. This is only recommended if you need to
       attach non-standard data to the bundle so that it is later available during
       the performance of actions (currently this would be restricted to the `ActionData`
       inner class of the bundle, but in the future the entire bundle may be in scope for
       actions and related work).
       
       An example of where this is appropriate: 
       Your `AssistantActionHandler` needs to know what to act on (can be done via 
       `ActionData#getActionArgument`) and what to apply. In this case, you may implement
       `ActionData`, adding a custom getter, `getFooValue`, and cast your `ActionData` being 
       passed in to `AssistantActionHandler#handleAction` to your custom `ActionData` so you
       may use the value.
       
   1. Optionally return an `AnalyticsProvider` so that common events that occur
   inside of the assistant may be tracked by your code. Examples include tracking the opening
   of the assistant or navigating to/from a tutorial.
   
1.  Register your (IntelliJ, not Assistant) action to open your assistant.
    ```xml
    <actions>
       <!-- TIP: You can reference IntelliJ icons (AllIcons) or AndroidIcons. If you want to declare
       your own icons, you have to declare your icon file under the package "icons " in order to reference it from xml.
       (see: http://www.jetbrains.org/intellij/sdk/docs/reference_guide/work_with_icons_and_images.html)
        -->
       <action
           id="ID_RETURNED_FROM_AssistantBundleCreator#getBundleId"
           class="OpenAssistSidePanelAction"
           icon="Icons.ICON_NAME"
           text="Action Label"
           description="Brief description of plugin">
         <add-to-group group-id="GroupToAddTo" />
       </action>
    </actions>
    ```
    
 1. Register your bundle creator and optional action support.
 
    ```xml
    <extensions defaultExtensionNs="com.android.tools.idea.assistant">
      <assistantBundleCreator implementation="com.foo.FooBundleCreator"/>
    </extensions>
    ```

 1. Optionally define "actions".
 
    Actions are a way to couple arbitrary functionality and state to your tutorials.
    They can be as simple as writing something to logcat or as complex as running
    static analysis on your project, talking to backends, adding code, etc
 
    1. Create your action.
        ```java
        public class FooAction implements AssistActionHandler {
        
          @NotNull
          @Override
          public String getId() {
            // Must be the same as the `key` in the below action configuration.
            return "myUniqueActionId";
          }
        
          @Override
          public void handleAction(@NotNull ActionData actionData, @NotNull Project project) {
            // Perform your action with the provided context
          }
        }
        ```

    1. Register the action.
       ```xml
       <extensions defaultExtensionNs="com.android.tools.idea.assistant">
         <assistantBundleCreator implementation="com.foo.FooBundleCreator"/>
  
         <actionHandler implementation="com.foo.FooAction"/>
       </extensions>
       ```

    1. Map your bundle configuration to the action.
       ```xml
       <stepElement>
         <action
           key="myUniqueActionId"
           label="Do Stuff!"
           successMessage="Stuff has been done">
         </action>
       </stepElement>
       ```

    1. Create a state manager (optional).
       ```java
       public class FooActionStateManager implements AssistActionStateManager {

         @NotNull
         @Override
         public String getId() {
           // Must be same as the action handler's id
           return "myUniqueActionId";
         }
       
         @Override
         public void init(@NotNull Project project, @NotNull ActionData actionData) {
           // register listeners or any other initialization
         }
       
         @Override
         public ActionState getState(@NotNull Project project, @NotNull ActionData actionData) {
           // calculate and return state
           return ActionState.COMPLETE;
         }
       
         @Nullable
         @Override
         public StatefulButtonMessage getStateDisplay(@NotNull Project project, @NotNull ActionData actionData, @Nullable String successMessage) {
           // Calculate the associated message and encapsulate in StatefulButtonMessage
           return new StatefulButtonMessage("success!", ActionState.COMPLETE);
         }
       }
       ```

    1. Register the state manager.
       ```xml
       <extensions defaultExtensionNs="com.android.tools.idea.assistant">
         <assistantBundleCreator implementation="com.foo.FooBundleCreator"/>
        
         <actionHandler implementation="com.foo.FooAction"/>
         <actionStateManager implementation="com.foo.FooActionStateManager"/>
       </extensions>
       ```

    1. Repeat as necessary, any number of `actionHandler` and `actionStateManager` instances
       map be added to your extensions.
