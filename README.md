<h1>Trust Manager module</h1>

The Trust Manager is a multithreaded module that periodically computes a new trust value for each node in each active context, and then updates the Contextual Ego Network with the newly computed trust value by storing it onto the corresponding edge.

 Each running thread of the Trust Manager corresponds to an active context; whenever a context becomes inactive, the respective thread is put to a wait state through a condition variable. 

Other HELIOS modules can then utilize the calculated trust value directly from the CEN and based on the user’s policy make decisions, e.g., what part of the profile can be shared, how urgent the message from an alter is to the ego, and so on.

<h2>About the module</h2>
<img src="docs/trust_module.png" width="70%">

The Trust Manager depends on the following HELIOS modules:
<ul>
	<li>Neuro-Behavioural Classifier module</li>
	<li>Contextual Ego Network Manager</li>
	<li>Proximity module</li>
	<li>Context Aware Profiling module</li>
</ul>

This module provides APIs to get trust scores related to the relationship between the ego and each alter in the Contextual Ego Network, differentiating them based on the context of reference. For each new alter, the module computes an initial trust value that only takes into consideration the information that is available at that stage; each trust value is then updated every set interval of time by also taking into account the information that can be derived from a sentimental analysis of the interactions, performed by the Neurobehavioural module.


### How to configure the dependencies ###

To manage project dependencies developed by the consortium, the approach proposed is to use a private Maven repository with Nexus.
To avoid clone all dependencies projects in local, to compile the "father" project. Otherwise, a developer should have all the projects locally to be able to compile. Using Nexus, the dependencies are located in a remote repository, available to compile, as described in the next section. Also to improve the automation for deploy, versioning and distribution of the project.

### How to use the HELIOS Nexus ###

Similar to other dependencies available in Maven Central, Google or others repositories. In this case we specify the Nexus
repository provided by Atos: `https://builder.helios-social.eu/repository/helios-repository/`

This URL makes the project dependencies available.

To access, we simply need credentials, that we will define locally in the variables `heliosUser` and `heliosPassword`.

The `build.gradle` of the project define the Nexus repository and the credential variables in this way:

```
repositories {
        ...
        maven {
            url "https://builder.helios-social.eu/repository/helios-repository/"
            credentials {
                username = heliosUser
                password = heliosPassword
            }
        }
    }
```

And the variables of Nexus's credentials are stored locally at `~/.gradle/gradle.properties`:

```
heliosUser=username
heliosPassword=password
```

To request Nexus username and password, contact with: `jordi.hernandezv@atos.net`

### How to use the dependencies ###

To use the dependency in `build.gradle` of the "father" project, you should specify the last version available in Nexus, related to the last Jenkins's deploy.
For example, to declare the dependency on the videocall module and the respective version:

`implementation 'eu.h2020.helios_social.core.trustmanager:trustmanager:1.0.19'`

For more info review: `https://scm.atosresearch.eu/ari/helios_group/generic-issues/blob/master/multiprojectDependencies.md`



<h2>How to use the module</h2>
To start the Trust Manager, it is necessary to instantiate a `TrustManager` object, by calling the constructor method, and then call the `startModule` method. The manager will automatically instantiate all the threads related to the active contexts.

The following methods are invoked automatically by the Trust Manager whenever precise events take place in the Contextual Ego Network. Such methods are invoked automatically by some callbacks that are registered on the Contextual Ego Network.

<ul>
	<li> Whenever a new context is added to the Contextual Ego Network, the <i>newContext</i> method is triggered: the Trust Manager instantiates a new thread for the new context, and from that moment on (up until such context is deactivated) trust values for the nodes in it are computed every deltaT seconds. </li>
	<li>Whenever a new alter is added to a context in the Contextual Ego Network, the <i>addAlterToContext</i> method is triggered: the thread related to that context is notified and gives an initial trust score to the new alter. From that moment on, up until such context is deactivated, trust values for the new alter in that specific context are computed every deltaT seconds.</li>
	<li>Whenever a context’s status is switched to active, the <i>activateContext</i> method is triggered: the Trust Manager notifies the thread related to that context, that from that moment on (up until such context is deactivated again) starts computing trust values for all the nodes in it every deltaT seconds.</li>
	<li>Whenever a context’s status is switched to inactive, the <i>deactivateContext</i> method is triggered: the Trust Manager notifies the thread related to that context, that computes a last set of trust scores for all the nodes in it and is then put on hold on a condition variable (up until such context is activated again).</li>
</ul>

To get the trust value computed between the user and one of its alters, the method `getTrust` must be called on the `TrustManager` object. The arguments required by the function should be retrieved from the same Contextual Ego Network instance passed to the constructor of the `TrustManager` object.

<h2>Project Structure</h2>
This project is structured as follows:
<ul>
	<li>The <b>src</b> directory contains the source code files.</li>
	<li>The <b>docs</b> directory contains the documentation for the source code.</li>
</ul>
