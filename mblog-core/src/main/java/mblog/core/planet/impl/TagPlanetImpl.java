/*********************************************************************
 * Copyright (c) 2014, 2015 mtons.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *********************************************************************/
package mblog.core.planet.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import mblog.core.planet.TagPlanet;
import mblog.data.Tag;
import mblog.persist.service.TagService;

/**
 * @author langhsu
 * 
 */
public class TagPlanetImpl implements TagPlanet {
	@Autowired
	private TagService tagService;

	@Override
	@Cacheable(value = "tagsCaches", key = "'top_tags_' + #maxResutls + '_' + #loadPost")
	public List<Tag> topTags(int maxResutls, boolean loadPost) {
		return tagService.topTags(maxResutls, loadPost);
	}

	@Override
	@CacheEvict(value = "tagsCaches", allEntries = true)
	public void delete(long id) {
		tagService.delete(id);
	}

	@Override
	@CacheEvict(value = "tagsCaches", allEntries = true)
	public boolean cacheFlush() {
		return Boolean.TRUE;
	}
}
