# By default, build image locally unless env variable is set to false
build_locally = os.getenv('TILT_BUILD_GROUP_SERVICE_LOCALLY', 'true').lower() == 'true'

# Build
if build_locally:
    custom_build(
        # Name of the container image
        ref = 'ghcr.io/grouphq/group-service',
        # Command to build the container image
        command = 'gradlew bootBuildImage --imageName %EXPECTED_REF%',
        # Files to watch that trigger a new build
        deps = ['build.gradle', 'src']
    )

# Deploy
if build_locally:
    k8s_yaml(kustomize('k8s/overlays/observability'))
else:
    k8s_yaml(kustomize('k8s/base'))

# Manage
k8s_resource('group-service', port_forwards=['9001'])