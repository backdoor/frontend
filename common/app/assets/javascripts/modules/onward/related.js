define([
    'common/common',
    'common/modules/lazyload',
    'common/modules/ui/expandable',
    'common/modules/ui/images'
], function (
    common,
    LazyLoad,
    Expandable,
    images
) {

    function related(config, context, url) {
        var container;

        if (config.page && config.page.hasStoryPackage) {

            new Expandable({
                dom: context.querySelector('.related-trails'),
                expanded: false,
                showCount: false
            }).init();
            common.mediator.emit('modules:related:loaded', config, context);

        } else if (config.switches && config.switches.relatedContent) {

            container = context.querySelector('.js-related');
            if (container) {
                new LazyLoad({
                    url: url || '/related/' + config.page.pageId + '.json',
                    container: container,
                    success: function () {
                        var relatedTrails = container.querySelector('.related-trails');
                        new Expandable({dom: relatedTrails, expanded: false, showCount: false}).init();
                        // upgrade images
                        images.upgrade(relatedTrails);
                        common.mediator.emit('modules:related:loaded', config, context);
                    },
                    error: function(req) {
                        common.mediator.emit('module:error', 'Failed to load related: ' + req.statusText, 'common/modules/related.js');
                    }
                }).load();
            }
        }

    }

    return related;
});
