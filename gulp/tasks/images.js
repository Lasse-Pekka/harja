var gulp = require('gulp');
var connect = require('gulp-connect');
var config = require('../config.js').images;

gulp.task('images', function() {
  gulp.src(config.src)
    .pipe(gulp.dest(config.dest))
    .pipe(connect.reload());
});
