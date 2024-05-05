default:
	just --justfile {{justfile()}} --list

# restart the hub server
restart-hub where:
	systemctl -H {{where}} restart HubServer

# restart the velocity server
restart-velocity where:
	systemctl -H {{where}} restart VelocityServer

# restart the main server
restart-main where:
	systemctl -H {{where}} restart MainServer

# copy the main server plugin
copy-main where:
	scp ./target/scala-3.3.1/civcubed_3-0.1.0-SNAPSHOT.jar {{where}}:/CivCubed/Main/plugins/CivCubed.jar

# copy the velocity server plugin
copy-velocity where:
	scp ./velocity-plugin/target/scala-3.3.1/civcubedvelocity_3-0.1.0-SNAPSHOT.jar {{where}}:/CivCubed/Velocity/plugins/CivCubedVelocityPlugin.jar

# copy the hub server plugin
copy-hub where:
	scp ./hub-plugin/target/scala-3.3.1/civcubedhub_3-0.1.0-SNAPSHOT.jar {{where}}:/CivCubed/Hub/plugins/CivCubedHub.jar

# copy the common code plugin
copy-common-code where:
	scp ./common-code/target/scala-3.3.1/civcubedcommoncode_3-0.1.0-SNAPSHOT.jar {{where}}:/CivCubed/CivCubedCommonCode.jar

# copy the dependency plugin
copy-dependency-plugin where:
	scp ./dependency-plugin/target/scala-3.3.1/CivCubedDependencyPlugin.jar {{where}}:/CivCubed/
