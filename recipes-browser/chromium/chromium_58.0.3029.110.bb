require chromium.inc
require chromium-unbundle.inc
require gn-utils.inc

inherit gtk-icon-cache qemu

OUTPUT_DIR = "out/Release"
B = "${S}/${OUTPUT_DIR}"

SRC_URI += " \
        file://v8-qemu-wrapper.patch \
        file://yocto-bug10635.patch \
        file://0001-Fix-GN-bootstrap.patch \
        file://0001-Build-fix-in-dmabuf-support.patch \
        file://0002-enable-i915-driver.patch \
        file://0003-Add-CrOS-source-files-for-display-management.patch \
        file://0004-ozone_demo.patch \
        file://0005-Make-mus_demo-work.patch \
        file://0006-Fix-the-display-size-problem.patch \
        file://0007-Make-it-Work-like-CrOS.patch \
        file://0008-Add-support-for-ash-host-window-bounds.patch \
        file://0009-Fix-crash-that-occures-when-handling-mouse-events.patch \
        file://0010-Enable-CMAA-in-Linux-on-Intel-GPU.patch \
        file://0011-Add-logs-to-see-whether-GPU-acceleration-features-ar.patch \
        file://0012-Add-support-for-native-gpu-memory-buffer-for-Chromiu.patch \
        file://0013-Intel-GPU-specific-optimization-in-GLES.patch \
        file://zero-copy-texture-upload.patch \
        file://video_acceleration.patch \
        file://increase_fd.patch \
        ${@bb.utils.contains('PACKAGECONFIG', 'root-profile', 'file://root-user-profile.patch', '', d)} \
        "

# At the moment, this recipe has only been tested on i586, x86-64, ARMv6,
# ARMv7a and aarch64.
COMPATIBLE_MACHINE = "(-)"
COMPATIBLE_MACHINE_aarch64 = "(.*)"
COMPATIBLE_MACHINE_armv6 = "(.*)"
COMPATIBLE_MACHINE_armv7a = "(.*)"
COMPATIBLE_MACHINE_x86 = "(.*)"
COMPATIBLE_MACHINE_x86-64 = "(.*)"

DEPENDS = "\
    alsa-lib \
    bison-native \
    dbus \
    expat \
    flac \
    fontconfig \
    freetype \
    glib-2.0 \
    gn-native \
    gperf-native \
    harfbuzz \
    hicolor-icon-theme \
    jpeg \
    libwebp \
    libva \
    libxml2 \
    libxslt \
    libxtst \
    ninja-native \
    nodejs-native \
    nspr \
    nss \
    pango \
    pciutils \
    pkgconfig-native \
    ${@bb.utils.contains('DISTRO_FEATURES', 'pulseaudio', 'pulseaudio', '', d)} \
    qemu-native \
    virtual/libgl \
    "
DEPENDS_append_x86 = "yasm-native"
DEPENDS_append_x86-64 = "yasm-native"

# The wrapper script we use from upstream requires bash.
RDEPENDS_${PN} = "bash"

PACKAGECONFIG ??= "ftp webrtc"
# ftp: Whether to build Chromium with support for the FTP protocol.
PACKAGECONFIG[ftp] = "disable_ftp_support=false,disable_ftp_support=true"
# proprietary-codecs: If enabled, this option will build Chromium with support
# for additional codecs in FFMPEG (such as the MPEG-LA ones). It is your
# responsibility to ensure you are complying with all required licenses.
PACKAGECONFIG[proprietary-codecs] = '\
        ffmpeg_branding="Chrome" proprietary_codecs=true, \
        '
# root-profile: If enabled, adds a patch to the Chromium binary wrapper that
# automatically sets root's user profile directory to /root/chromium-profile.
# DO NOT USE IN PRODUCTION: this is only supposed to ease debugging and
# testing.
PACKAGECONFIG[root-profile] = ",,,"
# webrtc: Whether to build Chromium with support for WebRTC.
PACKAGECONFIG[webrtc] = "enable_webrtc=true,enable_webrtc=false"

# The generated debug chrome binary is too big (~5Gb) for 32-bit systems.
# binutils, file and other utilities are unable to read it correctly and
# extract the debugging symbols from it.
TARGET_CFLAGS_remove_i586 = "${DEBUG_FLAGS}"
TARGET_CFLAGS_remove_armv6 = "${DEBUG_FLAGS}"
TARGET_CFLAGS_remove_armv7a = "${DEBUG_FLAGS}"

# Base GN arguments, mostly related to features we want to enable or disable.
GN_ARGS = "\
        ${PACKAGECONFIG_CONFARGS} \
        use_cups=false \
        use_gconf=false \
        use_gnome_keyring=false \
        use_kerberos=false \
        ffmpeg_branding="Chrome" \
        use_pulseaudio=${@bb.utils.contains('DISTRO_FEATURES', 'pulseaudio', 'true', 'false', d)} \
        use_system_libjpeg=true \
        "

# From Chromium's BUILDCONFIG.gn:
# Set to enable the official build level of optimization. This has nothing
# to do with branding, but enables an additional level of optimization above
# release (!is_debug). This might be better expressed as a tri-state
# (debug, release, official) but for historical reasons there are two
# separate flags.
# See also: https://groups.google.com/a/chromium.org/d/msg/chromium-dev/hkcb6AOX5gE/PPT1ukWoBwAJ
GN_ARGS += "is_debug=false is_official_build=true"

# Disable Chrome Remote Desktop (aka Chromoting) support. Building host support
# (so that the machine running this recipe can be controlled remotely from
# another machine) requires additional effort to build some extra binaries,
# whereas connecting to other machines requires building and installing a
# Chrome extension (it is not clear how to do that automatically).
GN_ARGS += "enable_remoting=false"

# NaCl support depends on the NaCl toolchain that needs to be built before NaCl
# itself. The whole process is quite cumbersome so we just disable the whole
# thing for now.
GN_ARGS += "enable_nacl=false"

# We do not want to use Chromium's own Debian-based sysroots, it is easier to
# just let Chromium's build system assume we are not using a sysroot at all and
# let Yocto handle everything.
GN_ARGS += "use_sysroot=false"

# Upstream Chromium uses clang on Linux, and GCC is not regularly tested. This
# means new GCC releases can introduce build failures as Chromium uses "-Wall
# -Werror" by default and we do not have much control over which warnings GCC
# decides to include in -Wall.
GN_ARGS += "treat_warnings_as_errors=false"

# Starting with M57 and https://codereview.chromium.org/2621193003,
# link-time optimization (LTO) is enabled by default on Linux x86_64
# builds, but the options are clang-specific and the builds are only
# tested with clang upstream.
GN_ARGS += "allow_posix_link_time_opt=false"

# Disable activation of field trial tests that can cause problems in
# production.
# See https://groups.google.com/a/chromium.org/d/msg/chromium-packagers/ECWC57W7E0k/9Kc5UAmyDAAJ
GN_ARGS += "fieldtrial_testing_like_official_build=true"

# Enable ozone_gbm
GN_ARGS += "use_ozone=true"
GN_ARGS += "ozone_platform_gbm=true"
GN_ARGS += "ozone_platform_x11=false"
GN_ARGS += "ozone_platform_wayland=false"

# Enable ozone-gbm only
GN_ARGS += "ozone_auto_platforms=false"

# Mash support
GN_ARGS += "enable_package_mash_services = true"

# VA support
GN_ARGS += "proprietary_codecs=true"
GN_ARGS += "use_v4l2_codec = false"
GN_ARGS += "enable_media_codec_video_decoder = true"

# API keys for accessing Google services. By default, we use an invalid key
# only to prevent the "you are missing an API key" infobar from being shown on
# startup.
# See https://dev.chromium.org/developers/how-tos/api-keys for more information
# on how to obtain your own keys. You can then set the variables below in
# local.conf or a .bbappend file.
GOOGLE_API_KEY ??= "invalid-api-key"
GOOGLE_DEFAULT_CLIENT_ID ??= "invalid-client-id"
GOOGLE_DEFAULT_CLIENT_SECRET ??= "invalid-client-secret"
GN_ARGS += '\
        google_api_key="${GOOGLE_API_KEY}" \
        ffmpeg_branding="Chrome" \
        google_default_client_id="${GOOGLE_DEFAULT_CLIENT_ID}" \
        google_default_client_secret="${GOOGLE_DEFAULT_CLIENT_SECRET}" \
        '

# Toolchains we will use for the build. We need to point to the toolchain file
# we've created, set the right target architecture and make sure we are not
# using Chromium's toolchain (bundled clang, bundled binutils etc).
GN_ARGS += '\
        custom_toolchain="//build/toolchain/yocto:yocto_target" \
        gold_path="" \
        host_toolchain="//build/toolchain/yocto:yocto_native" \
        is_clang=false \
        linux_use_bundled_binutils=false \
        target_cpu="${@gn_arch_name('${TUNE_ARCH}')}" \
        v8_snapshot_toolchain="//build/toolchain/yocto:yocto_target" \
        '

# ARM builds need special additional flags (see ${S}/build/config/arm.gni).
ARM_FLOAT_ABI = "${@bb.utils.contains('TUNE_FEATURES', 'callconvention-hard', 'hard', 'softfp', d)}"
GN_ARGS_append_armv6 = ' arm_version=6 arm_float_abi="${ARM_FLOAT_ABI}"'
GN_ARGS_append_armv7a = ' arm_version=7 arm_float_abi="${ARM_FLOAT_ABI}"'
# tcmalloc's atomicops-internals-arm-v6plus.h uses the "dmb" instruction that
# is not available on (some?) ARMv6 models, which causes the build to fail.
GN_ARGS_append_armv6 += 'use_allocator="none"'
# The WebRTC code fails to build on ARMv6 when NEON is enabled.
# https://bugs.chromium.org/p/webrtc/issues/detail?id=6574
GN_ARGS_append_armv6 += 'arm_use_neon=false'

# V8's JIT infrastructure requires binaries such as mksnapshot and
# mkpeephole to be run in the host during the build. However, these
# binaries must have the same bit-width as the target (e.g. a x86_64
# host targeting ARMv6 needs to produce a 32-bit binary). Instead of
# depending on a third Yocto toolchain, we just build those binaries
# for the target and run them on the host with QEMU.
python do_create_v8_qemu_wrapper () {
    """Creates a small wrapper that invokes QEMU to run some target V8 binaries
    on the host."""
    qemu_libdirs = [d.expand('${STAGING_DIR_HOST}${libdir}'),
                    d.expand('${STAGING_DIR_HOST}${base_libdir}')]
    qemu_cmd = qemu_wrapper_cmdline(d, d.getVar('STAGING_DIR_HOST', True),
                                    qemu_libdirs)
    wrapper_path = d.expand('${B}/v8-qemu-wrapper.sh')
    with open(wrapper_path, 'w') as wrapper_file:
        wrapper_file.write("""#!/bin/sh

# This file has been generated automatically.
# It invokes QEMU to run binaries built for the target in the host during the
# build process.

%s "$@"
""" % qemu_cmd)
    os.chmod(wrapper_path, 0o755)
}
do_create_v8_qemu_wrapper[dirs] = "${B}"
addtask create_v8_qemu_wrapper after do_patch before do_configure

python do_write_toolchain_file () {
    """Writes a BUILD.gn file for Yocto detailing its toolchains."""
    toolchain_dir = d.expand("${S}/build/toolchain/yocto")
    bb.utils.mkdirhier(toolchain_dir)
    toolchain_file = os.path.join(toolchain_dir, "BUILD.gn")
    write_toolchain_file(d, toolchain_file)
}
addtask write_toolchain_file after do_patch before do_configure

do_add_nodejs_symlink () {
	# Adds a symlink to the node binary to the location expected by
	# Chromium's build system.
	chromium_node_dir="${S}/third_party/node/linux/node-linux-x64/bin"
	nodejs_native_path="${STAGING_BINDIR_NATIVE}/node"
	mkdir -p "${chromium_node_dir}"
	if [ ! -f "${nodejs_native_path}" ]; then
		echo "${nodejs_native_path} does not exist."
		exit 1
	fi
	ln -sf "${nodejs_native_path}" "${chromium_node_dir}/node"
}
addtask add_nodejs_symlink after do_configure before do_compile

do_configure() {
	cd ${S}
	./build/linux/unbundle/replace_gn_files.py --system-libraries ${GN_UNBUNDLE_LIBS}
	gn gen --args='${GN_ARGS}' "${OUTPUT_DIR}"
}

do_compile() {
    DRV_I915=1 ninja -v "${PARALLEL_MAKE}" chrome chrome_sandbox ozone_demo gbm_unittests mash:all mus_demo video_decode_accelerator_unittest
}

do_install() {
	install -d ${D}${bindir}
	install -d ${D}${datadir}
	install -d ${D}${datadir}/applications
	install -d ${D}${datadir}/icons
	install -d ${D}${datadir}/icons/hicolor
	install -d ${D}${libdir}/chromium
	install -d ${D}${libdir}/chromium/locales

	# Process and install Chromium's template .desktop file.
	sed -e "s,@@MENUNAME@@,Chromium Browser,g" \
	    -e "s,@@PACKAGE@@,chromium,g" \
	    -e "s,@@USR_BIN_SYMLINK_NAME@@,chromium,g" \
	    ${S}/chrome/installer/linux/common/desktop.template > chromium.desktop
	install -m 0644 chromium.desktop ${D}${datadir}/applications

	# Install icons.
	for size in 22 24 48 64 128 256; do
		install -d ${D}${datadir}/icons/hicolor/${size}x${size}
		install -d ${D}${datadir}/icons/hicolor/${size}x${size}/apps
		install -m 0644 \
			${S}/chrome/app/theme/chromium/product_logo_${size}.png \
			${D}${datadir}/icons/hicolor/${size}x${size}/apps/chromium.png
	done

	# A wrapper for the proprietary Google Chrome version already exists.
	# We can just use that one instead of reinventing the wheel.
	WRAPPER_FILE=${S}/chrome/installer/linux/common/wrapper
	sed -e "s,@@CHANNEL@@,stable,g" \
		-e "s,@@PROGNAME@@,chromium-bin,g" \
		${WRAPPER_FILE} > chromium-wrapper
	install -m 0755 chromium-wrapper ${D}${libdir}/chromium/chromium-wrapper
	ln -s ${libdir}/chromium/chromium-wrapper ${D}${bindir}/chromium

	install -m 4755 chrome_sandbox ${D}${libdir}/chromium/chrome-sandbox
	install -m 0755 chrome ${D}${libdir}/chromium
	install -m 0755 ozone_demo ${D}${libdir}/chromium
	install -m 0755 mash ${D}${libdir}/chromium
	install -m 0755 *.service ${D}${libdir}/chromium
	install -m 0644 *.json ${D}${libdir}/chromium
	install -m 0644 *.pak ${D}${libdir}/chromium
    install -m 0644 *.so ${D}${libdir}/chromium/
	install -m 0644 *.bin ${D}${libdir}/chromium/
	install -m 0644 chrome_*.pak ${D}${libdir}/chromium/
	install -m 0644 icudtl.dat ${D}${libdir}/chromium/icudtl.dat
	install -m 0644 resources.pak ${D}${libdir}/chromium/resources.pak

	install -m 0644 locales/*.pak ${D}${libdir}/chromium/locales/
}

FILES_${PN} = " \
        ${bindir}/${PN} \
        ${datadir}/applications/${PN}.desktop \
        ${datadir}/icons/hicolor/*x*/apps/chromium.png \
        ${libdir}/${PN}/* \
        "
FILES_${PN}-dbg = "${libdir}/${PN}/.debug/"
PACKAGE_DEBUG_SPLIT_STYLE = "debug-without-src"

# There is no need to ship empty -dev packages.
ALLOW_EMPTY_${PN}-dev = "0"
